package com.glassthought.shepherd.core.executor

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.agent.facade.AgentFacade
import com.glassthought.shepherd.core.agent.facade.AgentPayload
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.context.AgentInstructionRequest
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.state.IterationConfig
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.state.SubPartStatus
import com.glassthought.shepherd.core.state.transitionTo
import com.glassthought.shepherd.core.state.validateCanSpawn
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.SubPartDoneContext
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCase

/**
 * Dependencies bundle for [PartExecutorImpl] — groups collaborators to stay within
 * the parameter-count threshold while preserving constructor injection.
 */
data class PartExecutorDeps(
    val agentFacade: AgentFacade,
    val contextForAgentProvider: ContextForAgentProvider,
    val gitCommitStrategy: GitCommitStrategy,
    val failedToConvergeUseCase: FailedToConvergeUseCase,
    val outFactory: OutFactory,
    val publicMdValidator: PublicMdValidator = PublicMdValidator(),
    val partCompletionGuard: PartCompletionGuard = PartCompletionGuard(),
    val innerFeedbackLoop: InnerFeedbackLoop? = null,
)

/**
 * Core implementation of [PartExecutor] — handles both doer-only and doer+reviewer parts.
 *
 * **Doer-only path** (`reviewerConfig == null`): spawn doer -> send instructions -> await signal
 * -> PUBLIC.md validation -> git commit -> map to PartResult.
 *
 * **Doer+reviewer path** (`reviewerConfig != null`): spawn doer -> send -> await COMPLETED ->
 * PUBLIC.md validation -> git commit -> lazily spawn reviewer (first iteration only) -> send ->
 * await -> on PASS: complete, on NEEDS_ITERATION: increment iteration, re-instruct doer with
 * reviewer's PUBLIC.md. Reviewer session stays alive for subsequent iterations.
 *
 * **R6 constraint**: NO Clock, SessionsState, AgentUnresponsiveUseCase, or
 * ContextWindowStateReader in constructor — those belong to AgentFacadeImpl.
 *
 * See spec: ref.ap.mxIc5IOj6qYI7vgLcpQn5.E (PartExecutor.md)
 */
@AnchorPoint("ap.8qYfNwR3xKpL2mZvJ5cTd.E")
@Suppress("TooManyFunctions")
class PartExecutorImpl(
    private val doerConfig: SubPartConfig,
    private val reviewerConfig: SubPartConfig?,
    private val deps: PartExecutorDeps,
    private val iterationConfig: IterationConfig,
) : PartExecutor {

    private val out = deps.outFactory.getOutForClass(PartExecutorImpl::class)
    private var currentIteration: Int = iterationConfig.current
    private var maxIterations: Int = iterationConfig.max
    private var doerStatus: SubPartStatus = SubPartStatus.NOT_STARTED
    private var reviewerStatus: SubPartStatus = SubPartStatus.NOT_STARTED

    override suspend fun execute(): PartResult =
        if (reviewerConfig == null) executeDoerOnly() else executeDoerWithReviewer(reviewerConfig)

    // ── Doer-only path ─────────────────────────────────────────────────

    private suspend fun executeDoerOnly(): PartResult {
        val handle = spawnSubPart(doerConfig, isDoer = true)
        return when (val signal = sendDoerInstructions(handle, reviewerPublicMdPath = null)) {
            is AgentSignal.Done -> mapDoerOnlyDone(signal, handle)
            is AgentSignal.FailWorkflow ->
                terminateWith(handle, null, SubPartRole.DOER, PartResult.FailedWorkflow(signal.reason))
            is AgentSignal.Crashed ->
                terminateWith(handle, null, SubPartRole.DOER, PartResult.AgentCrashed(signal.details))
            AgentSignal.SelfCompacted -> error(SELF_COMPACTED_UNEXPECTED)
        }
    }

    private suspend fun mapDoerOnlyDone(signal: AgentSignal.Done, handle: SpawnedAgentHandle): PartResult =
        when (signal.result) {
            DoneResult.COMPLETED -> {
                validatePublicMdOrCrash(doerConfig, handle, null)
                    ?: run {
                        // transitionTo called for validation only — throws on invalid state transition.
                        doerStatus.transitionTo(signal)
                        doerStatus = SubPartStatus.COMPLETED
                        afterDone(doerConfig, signal.result, handle)
                        killAllSessions(handle, null)
                        PartResult.Completed
                    }
            }
            DoneResult.PASS -> {
                killAllSessions(handle, null)
                error("Done(PASS) is invalid in doer-only path — only reviewers send PASS")
            }
            DoneResult.NEEDS_ITERATION -> {
                killAllSessions(handle, null)
                error("Done(NEEDS_ITERATION) is invalid in doer-only path — only reviewers send NEEDS_ITERATION")
            }
        }

    // ── Doer+Reviewer path ─────────────────────────────────────────────

    @Suppress("ReturnCount")
    private suspend fun executeDoerWithReviewer(revConfig: SubPartConfig): PartResult {
        val doerHandle = spawnSubPart(doerConfig, isDoer = true)
        var reviewerHandle: SpawnedAgentHandle? = null
        val doerSignal = sendDoerInstructions(doerHandle, reviewerPublicMdPath = null)

        val doerResult = mapDoerSignalInReviewerPath(doerSignal, doerHandle, reviewerHandle)
        if (doerResult != null) return doerResult

        // Lazy spawn: reviewer is created after doer's first Done(COMPLETED), per spec flow.
        reviewerHandle = spawnSubPart(revConfig, isDoer = false)

        var reviewerSignal = sendReviewerInstructions(reviewerHandle, revConfig)

        // Outer reviewer loop: each iteration handles one reviewer signal.
        // PASS -> complete. NEEDS_ITERATION -> inner loop + re-instruct reviewer.
        while (true) {
            val revResult = mapReviewerSignal(reviewerSignal, doerHandle, reviewerHandle, revConfig)
            if (revResult != null) return revResult

            // NEEDS_ITERATION: processNeedsIteration handles budget check + inner loop.
            // If it returns a PartResult, we're done (budget exhausted, crash, etc.)
            val needsIterResult = processNeedsIteration(
                doerHandle, reviewerHandle, revConfig,
            )
            if (needsIterResult != null) return needsIterResult

            // Re-instruct reviewer after inner loop.
            reviewerSignal = sendReviewerInstructions(reviewerHandle, revConfig)
        }
    }

    /** Returns [PartResult] to stop, or null to proceed to reviewer. */
    private suspend fun mapDoerSignalInReviewerPath(
        signal: AgentSignal,
        doerHandle: SpawnedAgentHandle,
        reviewerHandle: SpawnedAgentHandle?,
    ): PartResult? = when (signal) {
        is AgentSignal.Done -> {
            check(signal.result == DoneResult.COMPLETED) {
                "Doer in doer+reviewer path sent Done(${signal.result}) — expected COMPLETED"
            }
            validatePublicMdOrCrash(doerConfig, doerHandle, reviewerHandle)
                ?: run { afterDone(doerConfig, signal.result, doerHandle); null }
        }
        is AgentSignal.FailWorkflow ->
            terminateWith(doerHandle, reviewerHandle, SubPartRole.DOER, PartResult.FailedWorkflow(signal.reason))
        is AgentSignal.Crashed ->
            terminateWith(doerHandle, reviewerHandle, SubPartRole.DOER, PartResult.AgentCrashed(signal.details))
        AgentSignal.SelfCompacted -> error(SELF_COMPACTED_UNEXPECTED)
    }

    /**
     * Returns [PartResult] to stop, or null to continue iteration (NEEDS_ITERATION).
     *
     * PASS -> complete (validates, kills sessions, returns Completed).
     * NEEDS_ITERATION -> returns null (caller handles inner loop + re-instruction).
     * COMPLETED/FailWorkflow/Crashed/SelfCompacted -> terminal.
     */
    @Suppress("ReturnCount")
    private suspend fun mapReviewerSignal(
        signal: AgentSignal,
        doerHandle: SpawnedAgentHandle,
        reviewerHandle: SpawnedAgentHandle,
        revConfig: SubPartConfig,
    ): PartResult? {
        return when (signal) {
            is AgentSignal.Done -> when (signal.result) {
                DoneResult.PASS -> validatePublicMdOrCrash(revConfig, doerHandle, reviewerHandle)
                    ?: validateCompletionGuardOrCrash(revConfig, doerHandle, reviewerHandle)
                    ?: run {
                        reviewerStatus.transitionTo(signal)
                        reviewerStatus = SubPartStatus.COMPLETED
                        doerStatus = SubPartStatus.COMPLETED
                        afterDone(revConfig, signal.result, reviewerHandle)
                        killAllSessions(doerHandle, reviewerHandle)
                        PartResult.Completed
                    }
                DoneResult.NEEDS_ITERATION -> {
                    // Validate PUBLIC.md and do afterDone for the reviewer, but return null
                    // to signal the caller to run the inner feedback loop.
                    val crash = validatePublicMdOrCrash(revConfig, doerHandle, reviewerHandle)
                    if (crash != null) return crash
                    reviewerStatus.transitionTo(signal)
                    afterDone(revConfig, signal.result, reviewerHandle)
                    null
                }
                DoneResult.COMPLETED -> {
                    killAllSessions(doerHandle, reviewerHandle)
                    error("Reviewer sent Done(COMPLETED) — expected PASS or NEEDS_ITERATION")
                }
            }
            is AgentSignal.FailWorkflow -> terminateWith(
                doerHandle, reviewerHandle, SubPartRole.REVIEWER,
                PartResult.FailedWorkflow(signal.reason),
            )
            is AgentSignal.Crashed -> terminateWith(
                doerHandle, reviewerHandle, SubPartRole.REVIEWER,
                PartResult.AgentCrashed(signal.details),
            )
            AgentSignal.SelfCompacted -> error(SELF_COMPACTED_UNEXPECTED)
        }
    }

    /**
     * Handles the NEEDS_ITERATION flow: iteration budget check, inner feedback loop.
     *
     * Called AFTER mapReviewerSignal has validated PUBLIC.md and called afterDone.
     * Returns [PartResult] if a terminal condition is reached (budget exhausted, crash),
     * or null to continue to reviewer re-instruction.
     *
     * R10: iteration.current increments once per needs_iteration, NOT per feedback item.
     */
    @Suppress("ReturnCount")
    private suspend fun processNeedsIteration(
        doerHandle: SpawnedAgentHandle,
        reviewerHandle: SpawnedAgentHandle,
        revConfig: SubPartConfig,
    ): PartResult? {
        // R10: iteration counter increments once per reviewer needs_iteration
        currentIteration++

        // Budget check
        if (currentIteration >= maxIterations) {
            val granted = deps.failedToConvergeUseCase
                .askForMoreIterations(maxIterations, currentIteration)
            if (granted) {
                maxIterations += ITERATION_INCREMENT
                out.info("iteration_budget_extended") {
                    listOf(
                        Val(maxIterations.toString(), ShepherdValType.MAX_ITERATIONS),
                        Val(currentIteration.toString(), ShepherdValType.ITERATION_COUNT),
                    )
                }
            } else {
                killAllSessions(doerHandle, reviewerHandle)
                return PartResult.FailedToConverge(
                    "Iteration budget exhausted after " +
                        "$currentIteration iterations"
                )
            }
        }

        // Inner feedback loop (R3, R9, R11)
        val innerLoop = deps.innerFeedbackLoop ?: return null
        val feedbackDir = revConfig.feedbackDir
            ?: error("Reviewer config must have feedbackDir")

        val innerResult = innerLoop.execute(
            InnerLoopContext(
                doerHandle = doerHandle,
                reviewerHandle = reviewerHandle,
                feedbackDir = feedbackDir,
                doerConfig = doerConfig,
                currentIteration = currentIteration,
                maxIterations = maxIterations,
            )
        )

        return when (innerResult) {
            is InnerLoopOutcome.Terminate -> {
                killAllSessions(doerHandle, reviewerHandle)
                innerResult.result
            }
            is InnerLoopOutcome.Continue -> null
        }
    }

    // ── Infrastructure helpers ──────────────────────────────────────────

    private suspend fun spawnSubPart(config: SubPartConfig, isDoer: Boolean): SpawnedAgentHandle {
        if (isDoer) { doerStatus.validateCanSpawn(); doerStatus = SubPartStatus.IN_PROGRESS }
        else { reviewerStatus.validateCanSpawn(); reviewerStatus = SubPartStatus.IN_PROGRESS }
        out.info(if (isDoer) "spawning_doer" else "spawning_reviewer") {
            listOf(Val(config.subPartName, ShepherdValType.SUB_PART_NAME))
        }
        return deps.agentFacade.spawnAgent(config.toSpawnAgentConfig())
    }

    private suspend fun sendDoerInstructions(handle: SpawnedAgentHandle, reviewerPublicMdPath: java.nio.file.Path?) =
        deps.agentFacade.sendPayloadAndAwaitSignal(handle, AgentPayload(
            deps.contextForAgentProvider.assembleInstructions(
                AgentInstructionRequest.DoerRequest(
                    roleDefinition = doerConfig.roleDefinition, ticketContent = doerConfig.ticketContent,
                    iterationNumber = currentIteration, outputDir = doerConfig.outputDir,
                    publicMdOutputPath = doerConfig.publicMdOutputPath, privateMdPath = doerConfig.privateMdPath,
                    executionContext = doerConfig.executionContext, reviewerPublicMdPath = reviewerPublicMdPath,
                )
            )
        ))

    private suspend fun sendReviewerInstructions(handle: SpawnedAgentHandle, revConfig: SubPartConfig) =
        deps.agentFacade.sendPayloadAndAwaitSignal(handle, AgentPayload(
            deps.contextForAgentProvider.assembleInstructions(
                AgentInstructionRequest.ReviewerRequest(
                    roleDefinition = revConfig.roleDefinition, ticketContent = revConfig.ticketContent,
                    iterationNumber = currentIteration, outputDir = revConfig.outputDir,
                    publicMdOutputPath = revConfig.publicMdOutputPath, privateMdPath = revConfig.privateMdPath,
                    executionContext = revConfig.executionContext, doerPublicMdPath = doerConfig.publicMdOutputPath,
                    feedbackDir = revConfig.feedbackDir ?: error("Reviewer config must have feedbackDir"),
                )
            )
        ))

    /**
     * Part Completion Guard (Gate 5, R8): validates no critical/important feedback in pending/.
     * Returns [PartResult.AgentCrashed] if blocking items found, null if guard passes.
     * Only applies when the reviewer config has a feedbackDir; otherwise passes trivially.
     */
    @Suppress("ReturnCount")
    private suspend fun validateCompletionGuardOrCrash(
        revConfig: SubPartConfig, doerHandle: SpawnedAgentHandle, reviewerHandle: SpawnedAgentHandle,
    ): PartResult.AgentCrashed? {
        val feedbackDir = revConfig.feedbackDir ?: return null
        val pendingDir = feedbackDir.resolve(PENDING_DIR)
        val addressedDir = feedbackDir.resolve(ADDRESSED_DIR)
        val result = deps.partCompletionGuard.validate(pendingDir, addressedDir)
        if (result is PartCompletionGuard.GuardResult.Failed) {
            reviewerStatus = SubPartStatus.FAILED
            killAllSessions(doerHandle, reviewerHandle)
            return PartResult.AgentCrashed(result.message)
        }
        return null
    }

    private suspend fun validatePublicMdOrCrash(
        config: SubPartConfig, doerHandle: SpawnedAgentHandle, reviewerHandle: SpawnedAgentHandle?,
    ): PartResult.AgentCrashed? {
        val result = deps.publicMdValidator.validate(config.publicMdOutputPath, config.subPartName)
        if (result is PublicMdValidator.ValidationResult.Invalid) {
            if (config.subPartRole == SubPartRole.DOER) doerStatus = SubPartStatus.FAILED
            else reviewerStatus = SubPartStatus.FAILED
            killAllSessions(doerHandle, reviewerHandle)
            return PartResult.AgentCrashed(result.message)
        }
        return null
    }

    private suspend fun afterDone(config: SubPartConfig, result: DoneResult, handle: SpawnedAgentHandle) {
        deps.gitCommitStrategy.onSubPartDone(SubPartDoneContext(
            partName = config.partName, subPartName = config.subPartName,
            subPartRole = config.subPartRole.name, result = result.name,
            hasReviewer = reviewerConfig != null, currentIteration = currentIteration,
            maxIterations = maxIterations, agentType = config.agentType, model = config.model,
        ))
        val state = deps.agentFacade.readContextWindowState(handle)
        out.debug("context_window_state_at_done_boundary") {
            listOf(Val(state.remainingPercentage?.toString() ?: "unknown", ShepherdValType.CONTEXT_WINDOW_REMAINING))
        }
    }

    private suspend fun killAllSessions(doer: SpawnedAgentHandle, reviewer: SpawnedAgentHandle?) {
        deps.agentFacade.killSession(doer)
        reviewer?.let { deps.agentFacade.killSession(it) }
    }

    private suspend fun terminateWith(
        doer: SpawnedAgentHandle, reviewer: SpawnedAgentHandle?, role: SubPartRole, result: PartResult,
    ): PartResult {
        if (role == SubPartRole.DOER) doerStatus = SubPartStatus.FAILED else reviewerStatus = SubPartStatus.FAILED
        killAllSessions(doer, reviewer)
        return result
    }

    companion object {
        private const val ITERATION_INCREMENT = 2
        private const val PENDING_DIR = "pending"
        private const val ADDRESSED_DIR = "addressed"
        private const val SELF_COMPACTED_UNEXPECTED =
            "SelfCompacted should not reach PartExecutorImpl — handled inside AgentFacade"
    }
}
