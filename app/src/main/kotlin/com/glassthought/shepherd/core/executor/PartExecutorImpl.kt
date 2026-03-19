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
import com.glassthought.shepherd.core.compaction.CompactionTrigger
import com.glassthought.shepherd.core.compaction.SelfCompactionInstructionBuilder
import com.glassthought.shepherd.core.context.AgentInstructionRequest
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.state.IterationConfig
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.state.SubPartStatus
import com.glassthought.shepherd.core.state.transitionTo
import com.glassthought.shepherd.core.state.validateCanSpawn
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.SubPartDoneContext
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCase
import java.nio.file.Files

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
    val harnessTimeoutConfig: HarnessTimeoutConfig = HarnessTimeoutConfig.defaults(),
    val selfCompactionInstructionBuilder: SelfCompactionInstructionBuilder = SelfCompactionInstructionBuilder(),
    val privateMdValidator: PrivateMdValidator = PrivateMdValidator(),
)

/**
 * Core implementation of [PartExecutor] — handles both doer-only and doer+reviewer parts.
 *
 * **Doer-only path** (`reviewerConfig == null`): spawn doer -> send instructions -> await signal
 * -> PUBLIC.md validation -> git commit -> context window check -> optional compaction -> map to PartResult.
 *
 * **Doer+reviewer path** (`reviewerConfig != null`): spawn doer -> send -> await COMPLETED ->
 * PUBLIC.md validation -> git commit -> context window check -> optional compaction ->
 * lazily spawn reviewer (first iteration only) -> send ->
 * await -> on PASS: complete, on NEEDS_ITERATION: increment iteration, re-instruct doer with
 * reviewer's PUBLIC.md. Reviewer session stays alive for subsequent iterations (unless compacted).
 *
 * **Self-compaction** (ref.ap.8nwz2AHf503xwq8fKuLcl.E): After every Done signal, reads context
 * window state. If remaining percentage <= soft threshold, performs controlled compaction:
 * send compaction instruction -> await SelfCompacted -> validate PRIVATE.md -> git commit ->
 * kill session -> set handle = null. Next iteration lazily respawns with PRIVATE.md in instructions.
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
                        val compactionOutcome = afterDone(doerConfig, signal.result, handle)
                        when (compactionOutcome) {
                            is CompactionOutcome.NoCompaction -> {
                                killAllSessions(handle, null)
                                PartResult.Completed
                            }
                            is CompactionOutcome.Compacted -> {
                                // Session already killed during compaction. Part is done.
                                PartResult.Completed
                            }
                            is CompactionOutcome.CompactionFailed -> {
                                PartResult.AgentCrashed(compactionOutcome.reason)
                            }
                        }
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
        var doerHandle: SpawnedAgentHandle? = spawnSubPart(doerConfig, isDoer = true)
        var reviewerHandle: SpawnedAgentHandle? = null
        var doerSignal = sendDoerInstructions(doerHandle!!, reviewerPublicMdPath = null)

        while (true) {
            val doerResult = mapDoerSignalInReviewerPath(doerSignal, doerHandle!!, reviewerHandle)
            if (doerResult is DoerSignalResult.Terminal) return doerResult.partResult

            // After afterDone, doerHandle may have been compacted
            if (doerResult is DoerSignalResult.Continue && doerResult.doerCompacted) {
                doerHandle = null
            }

            // Lazy spawn: reviewer is created after doer's first Done(COMPLETED), per spec flow.
            // Subsequent iterations reuse the already-alive reviewer session.
            if (reviewerHandle == null) {
                reviewerHandle = spawnSubPart(revConfig, isDoer = false)
            }

            val revResult = mapReviewerSignal(
                sendReviewerInstructions(reviewerHandle, revConfig), doerHandle, reviewerHandle, revConfig
            )
            if (revResult is ReviewerSignalResult.Terminal) return revResult.partResult

            // After reviewer afterDone, reviewer may have been compacted
            if (revResult is ReviewerSignalResult.Continue && revResult.reviewerCompacted) {
                reviewerHandle = null
            }

            // Respawn compacted sub-parts (lazy respawn — session rotation)
            if (doerHandle == null) {
                doerHandle = respawnAfterCompaction(doerConfig)
            }
            if (reviewerHandle == null) {
                reviewerHandle = respawnAfterCompaction(revConfig)
            }
            doerSignal = sendDoerInstructions(doerHandle, reviewerPublicMdPath = revConfig.publicMdOutputPath)
        }
    }

    /** Result of processing a doer signal in the reviewer path. */
    private sealed class DoerSignalResult {
        /** Part terminated — return this result. */
        data class Terminal(val partResult: PartResult) : DoerSignalResult()
        /** Proceed to reviewer. [doerCompacted] indicates the doer handle was killed via compaction. */
        data class Continue(val doerCompacted: Boolean = false) : DoerSignalResult()
    }

    /** Returns [DoerSignalResult.Terminal] to stop, or [DoerSignalResult.Continue] to proceed to reviewer. */
    @Suppress("NestedBlockDepth")
    private suspend fun mapDoerSignalInReviewerPath(
        signal: AgentSignal,
        doerHandle: SpawnedAgentHandle,
        reviewerHandle: SpawnedAgentHandle?,
    ): DoerSignalResult = when (signal) {
        is AgentSignal.Done -> {
            check(signal.result == DoneResult.COMPLETED) {
                "Doer in doer+reviewer path sent Done(${signal.result}) — expected COMPLETED"
            }
            val crash = validatePublicMdOrCrash(doerConfig, doerHandle, reviewerHandle)
            if (crash != null) {
                DoerSignalResult.Terminal(crash)
            } else {
                when (val outcome = afterDone(doerConfig, signal.result, doerHandle)) {
                    is CompactionOutcome.NoCompaction -> DoerSignalResult.Continue(doerCompacted = false)
                    is CompactionOutcome.Compacted -> DoerSignalResult.Continue(doerCompacted = true)
                    is CompactionOutcome.CompactionFailed -> {
                        reviewerHandle?.let { deps.agentFacade.killSession(it) }
                        DoerSignalResult.Terminal(PartResult.AgentCrashed(outcome.reason))
                    }
                }
            }
        }
        is AgentSignal.FailWorkflow -> DoerSignalResult.Terminal(
            terminateWith(doerHandle, reviewerHandle, SubPartRole.DOER, PartResult.FailedWorkflow(signal.reason))
        )
        is AgentSignal.Crashed -> DoerSignalResult.Terminal(
            terminateWith(doerHandle, reviewerHandle, SubPartRole.DOER, PartResult.AgentCrashed(signal.details))
        )
        AgentSignal.SelfCompacted -> error(SELF_COMPACTED_UNEXPECTED)
    }

    /** Result of processing a reviewer signal. */
    private sealed class ReviewerSignalResult {
        data class Terminal(val partResult: PartResult) : ReviewerSignalResult()
        data class Continue(val reviewerCompacted: Boolean = false) : ReviewerSignalResult()
    }

    /** Returns [ReviewerSignalResult.Terminal] to stop, or [ReviewerSignalResult.Continue] to continue iteration. */
    @Suppress("ReturnCount")
    private suspend fun mapReviewerSignal(
        signal: AgentSignal,
        doerHandle: SpawnedAgentHandle?,
        reviewerHandle: SpawnedAgentHandle,
        revConfig: SubPartConfig,
    ): ReviewerSignalResult = when (signal) {
        is AgentSignal.Done -> when (signal.result) {
            DoneResult.PASS -> {
                val crash = validatePublicMdOrCrash(revConfig, doerHandle, reviewerHandle)
                if (crash != null) {
                    ReviewerSignalResult.Terminal(crash)
                } else {
                    reviewerStatus.transitionTo(signal)
                    reviewerStatus = SubPartStatus.COMPLETED
                    doerStatus = SubPartStatus.COMPLETED
                    afterDone(revConfig, signal.result, reviewerHandle)
                    killAllSessions(doerHandle, reviewerHandle)
                    ReviewerSignalResult.Terminal(PartResult.Completed)
                }
            }
            DoneResult.NEEDS_ITERATION ->
                processNeedsIteration(signal, doerHandle, reviewerHandle, revConfig)
            DoneResult.COMPLETED -> {
                killAllSessions(doerHandle, reviewerHandle)
                error("Reviewer sent Done(COMPLETED) — expected PASS or NEEDS_ITERATION")
            }
        }
        is AgentSignal.FailWorkflow -> ReviewerSignalResult.Terminal(
            terminateWith(doerHandle, reviewerHandle, SubPartRole.REVIEWER, PartResult.FailedWorkflow(signal.reason))
        )
        is AgentSignal.Crashed -> ReviewerSignalResult.Terminal(
            terminateWith(doerHandle, reviewerHandle, SubPartRole.REVIEWER, PartResult.AgentCrashed(signal.details))
        )
        AgentSignal.SelfCompacted -> error(SELF_COMPACTED_UNEXPECTED)
    }

    @Suppress("ReturnCount")
    private suspend fun processNeedsIteration(
        signal: AgentSignal.Done,
        doerHandle: SpawnedAgentHandle?,
        reviewerHandle: SpawnedAgentHandle,
        revConfig: SubPartConfig,
    ): ReviewerSignalResult {
        val crash = validatePublicMdOrCrash(revConfig, doerHandle, reviewerHandle)
        if (crash != null) return ReviewerSignalResult.Terminal(crash)
        reviewerStatus.transitionTo(signal)

        val compactionOutcome = afterDone(revConfig, signal.result, reviewerHandle)
        when (compactionOutcome) {
            is CompactionOutcome.CompactionFailed -> {
                doerHandle?.let { deps.agentFacade.killSession(it) }
                return ReviewerSignalResult.Terminal(PartResult.AgentCrashed(compactionOutcome.reason))
            }
            else -> { /* proceed */ }
        }

        currentIteration++
        if (currentIteration < maxIterations) {
            return ReviewerSignalResult.Continue(
                reviewerCompacted = compactionOutcome is CompactionOutcome.Compacted
            )
        }
        val granted = deps.failedToConvergeUseCase.askForMoreIterations(maxIterations, currentIteration)
        if (granted) {
            maxIterations += ITERATION_INCREMENT
            out.info("iteration_budget_extended") {
                listOf(
                    Val(maxIterations.toString(), ShepherdValType.MAX_ITERATIONS),
                    Val(currentIteration.toString(), ShepherdValType.ITERATION_COUNT),
                )
            }
            return ReviewerSignalResult.Continue(
                reviewerCompacted = compactionOutcome is CompactionOutcome.Compacted
            )
        }
        killAllSessions(doerHandle, reviewerHandle)
        return ReviewerSignalResult.Terminal(
            PartResult.FailedToConverge("Iteration budget exhausted after $currentIteration iterations")
        )
    }

    // ── Self-compaction ────────────────────────────────────────────────

    /**
     * Outcome of the done-boundary compaction check in [afterDone].
     */
    private sealed class CompactionOutcome {
        /** Context is healthy or stale — no compaction triggered. */
        data object NoCompaction : CompactionOutcome()
        /** Compaction succeeded — session was killed, handle is invalid. */
        data object Compacted : CompactionOutcome()
        /** Compaction was attempted but failed — caller should return AgentCrashed. */
        data class CompactionFailed(val reason: String) : CompactionOutcome()
    }

    /**
     * Performs controlled self-compaction at a done boundary.
     *
     * Flow:
     * 1. Build compaction instruction via [SelfCompactionInstructionBuilder]
     * 2. Send via [AgentFacade.sendPayloadAndAwaitSignal] — expects [AgentSignal.SelfCompacted]
     * 3. Validate PRIVATE.md exists and is non-empty
     * 4. Git commit (captures PRIVATE.md)
     * 5. Kill session
     *
     * See spec: ref.ap.8nwz2AHf503xwq8fKuLcl.E
     */
    @Suppress("UnusedParameter")
    private suspend fun performCompaction(
        handle: SpawnedAgentHandle,
        config: SubPartConfig,
        trigger: CompactionTrigger,
    ): CompactionOutcome {
        out.info("starting_self_compaction") {
            listOf(
                Val(config.subPartName, ShepherdValType.SUB_PART_NAME),
                Val(trigger.name, ShepherdValType.COMPACTION_TRIGGER),
            )
        }

        // Build compaction instruction and write to temp file
        val privateMdPath = config.privateMdPath
            ?: error("Cannot perform compaction: privateMdPath is null for sub-part [${config.subPartName}]")

        val instructionText = deps.selfCompactionInstructionBuilder.build(privateMdPath)
        val instructionFile = Files.createTempFile("compaction-instruction-", ".md")
        Files.writeString(instructionFile, instructionText)

        // Send compaction instruction and await signal
        val signal = deps.agentFacade.sendPayloadAndAwaitSignal(handle, AgentPayload(instructionFile))

        return when (signal) {
            AgentSignal.SelfCompacted -> {
                // Validate PRIVATE.md exists and is non-empty
                val validation = deps.privateMdValidator.validate(privateMdPath, config.subPartName)
                if (validation is PrivateMdValidator.ValidationResult.Invalid) {
                    deps.agentFacade.killSession(handle)
                    return CompactionOutcome.CompactionFailed(validation.message)
                }

                // Git commit captures PRIVATE.md
                deps.gitCommitStrategy.onSubPartDone(SubPartDoneContext(
                    partName = config.partName, subPartName = config.subPartName,
                    subPartRole = config.subPartRole.name, result = "SELF_COMPACTED",
                    hasReviewer = reviewerConfig != null, currentIteration = currentIteration,
                    maxIterations = maxIterations, agentType = config.agentType, model = config.model,
                ))

                // Kill old session — handle becomes invalid
                deps.agentFacade.killSession(handle)

                out.info("self_compaction_complete") {
                    listOf(Val(config.subPartName, ShepherdValType.SUB_PART_NAME))
                }

                CompactionOutcome.Compacted
            }
            is AgentSignal.Done -> {
                deps.agentFacade.killSession(handle)
                CompactionOutcome.CompactionFailed(
                    "Agent [${config.subPartName}] cannot follow compaction protocol — " +
                        "sent Done(${signal.result}) instead of SelfCompacted"
                )
            }
            is AgentSignal.Crashed -> {
                // Agent crashed during compaction (e.g., timeout)
                CompactionOutcome.CompactionFailed(signal.details)
            }
            is AgentSignal.FailWorkflow -> {
                deps.agentFacade.killSession(handle)
                CompactionOutcome.CompactionFailed(
                    "Agent [${config.subPartName}] sent FailWorkflow during compaction: ${signal.reason}"
                )
            }
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

    /**
     * Respawns a sub-part after self-compaction killed the previous session.
     *
     * Unlike [spawnSubPart], this does NOT validate the NOT_STARTED state transition because
     * the sub-part status remains IN_PROGRESS during session rotation. The sub-part is still
     * logically in-progress — it just got a fresh context window.
     *
     * See spec: ref.ap.8nwz2AHf503xwq8fKuLcl.E (session rotation)
     */
    private suspend fun respawnAfterCompaction(config: SubPartConfig): SpawnedAgentHandle {
        out.info("respawning_after_compaction") {
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

    private suspend fun validatePublicMdOrCrash(
        config: SubPartConfig, doerHandle: SpawnedAgentHandle?, reviewerHandle: SpawnedAgentHandle?,
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

    /**
     * Post-done processing: git commit, context window check, optional compaction.
     *
     * Returns [CompactionOutcome] indicating whether compaction was triggered and its result.
     * If compaction succeeds, the handle has been killed and must not be used further.
     */
    @Suppress("ReturnCount")
    private suspend fun afterDone(
        config: SubPartConfig,
        result: DoneResult,
        handle: SpawnedAgentHandle,
    ): CompactionOutcome {
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

        // Done-boundary compaction trigger detection (ref.ap.8nwz2AHf503xwq8fKuLcl.E)
        val remaining = state.remainingPercentage
        if (remaining == null) {
            out.warn(
                "stale_context_window_state_skipping_compaction",
                Val(config.subPartName, ShepherdValType.SUB_PART_NAME),
            )
            return CompactionOutcome.NoCompaction
        }

        if (remaining <= deps.harnessTimeoutConfig.contextWindowSoftThresholdPct) {
            return performCompaction(handle, config, CompactionTrigger.DONE_BOUNDARY)
        }

        return CompactionOutcome.NoCompaction
    }

    private suspend fun killAllSessions(doer: SpawnedAgentHandle?, reviewer: SpawnedAgentHandle?) {
        doer?.let { deps.agentFacade.killSession(it) }
        reviewer?.let { deps.agentFacade.killSession(it) }
    }

    private suspend fun terminateWith(
        doer: SpawnedAgentHandle?, reviewer: SpawnedAgentHandle?, role: SubPartRole, result: PartResult,
    ): PartResult {
        if (role == SubPartRole.DOER) doerStatus = SubPartStatus.FAILED else reviewerStatus = SubPartStatus.FAILED
        killAllSessions(doer, reviewer)
        return result
    }

    companion object {
        private const val ITERATION_INCREMENT = 2
        private const val SELF_COMPACTED_UNEXPECTED =
            "SelfCompacted should not reach PartExecutorImpl — handled inside AgentFacade"
    }
}
