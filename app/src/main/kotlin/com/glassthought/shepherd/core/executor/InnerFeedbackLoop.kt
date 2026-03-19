package com.glassthought.shepherd.core.executor

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.out.Out
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.agent.facade.AgentFacade
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.SubPartDoneContext
import com.glassthought.shepherd.feedback.FeedbackResolution
import com.glassthought.shepherd.feedback.FeedbackResolutionParser
import com.glassthought.shepherd.feedback.ParseResult
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwait
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructOutcome
import com.glassthought.shepherd.usecase.rejectionnegotiation.FeedbackFileReader
import com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionNegotiationUseCase
import com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name

/**
 * Outcome of the inner feedback loop.
 *
 * - [Continue] — inner loop completed, ready for reviewer re-instruction.
 * - [Terminate] — terminal condition (crash, fail-workflow); return this result.
 */
sealed class InnerLoopOutcome {
    object Continue : InnerLoopOutcome()
    data class Terminate(val result: PartResult) : InnerLoopOutcome()
}

/**
 * Dependencies bundle for [InnerFeedbackLoop] — groups collaborators
 * to stay within the parameter-count threshold while preserving constructor injection.
 */
data class InnerFeedbackLoopDeps(
    val reInstructAndAwait: ReInstructAndAwait,
    val rejectionNegotiationUseCase: RejectionNegotiationUseCase,
    val contextForAgentProvider: ContextForAgentProvider,
    val agentFacade: AgentFacade,
    val gitCommitStrategy: GitCommitStrategy,
    val publicMdValidator: PublicMdValidator,
    val feedbackFileReader: FeedbackFileReader,
    val outFactory: OutFactory,
)

/**
 * Execution context for a single inner feedback loop invocation.
 * Groups parameters that change per-invocation to reduce method parameter count.
 */
data class InnerLoopContext(
    val doerHandle: SpawnedAgentHandle,
    val reviewerHandle: SpawnedAgentHandle,
    val feedbackDir: Path,
    val doerConfig: SubPartConfig,
    val currentIteration: Int,
    val maxIterations: Int,
) {
    val pendingDir: Path get() = feedbackDir.resolve(PENDING)
    val addressedDir: Path get() = feedbackDir.resolve(ADDRESSED)
    val rejectedDir: Path get() = feedbackDir.resolve(REJECTED)

    companion object {
        private const val PENDING = "pending"
        private const val ADDRESSED = "addressed"
        private const val REJECTED = "rejected"
    }
}

/**
 * Orchestrates the inner feedback loop within a single iteration of
 * the doer+reviewer cycle.
 *
 * After the reviewer signals `needs_iteration`, this class:
 * 1. Validates at least one file exists in `pending/` (R9 guard).
 * 2. Processes feedback files in severity order: critical -> important -> optional.
 * 3. For each file: re-instructs the doer, reads the resolution marker, moves the file.
 * 4. Validates no critical/important files remain in `pending/` (bug guard).
 *
 * **SRP**: PartExecutorImpl owns the outer loop (doer -> reviewer -> iteration);
 * this class owns the inner loop (per-feedback-item processing within one iteration).
 *
 * See spec: ref.ap.5Y5s8gqykzGN1TVK5MZdS.E (granular-feedback-loop.md)
 */
@AnchorPoint("ap.InnerFeedbackLoop.E")
class InnerFeedbackLoop(private val deps: InnerFeedbackLoopDeps) {

    private val out: Out = deps.outFactory.getOutForClass(InnerFeedbackLoop::class)

    /**
     * Executes the inner feedback loop for all pending feedback files.
     *
     * @return [InnerLoopOutcome.Continue] if all items processed,
     *   or [InnerLoopOutcome.Terminate] on failure.
     */
    @Suppress("ReturnCount")
    suspend fun execute(ctx: InnerLoopContext): InnerLoopOutcome {
        // R9: Feedback files presence guard
        val pendingFiles = listPendingFiles(ctx.pendingDir)
        if (pendingFiles.isEmpty()) {
            return InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(
                    "Reviewer signaled needs_iteration without writing " +
                        "feedback files to ${ctx.pendingDir}"
                )
            )
        }

        out.info("inner_feedback_loop_started") {
            listOf(
                Val(
                    pendingFiles.size.toString(),
                    ShepherdValType.FEEDBACK_FILE_COUNT,
                )
            )
        }

        // Validate all files have recognized severity prefixes (typo guard)
        val unrecognized = findUnrecognizedPrefixFiles(pendingFiles)
        if (unrecognized.isNotEmpty()) {
            return InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(
                    "Feedback files with unrecognized severity prefix " +
                        "in ${ctx.pendingDir}: ${unrecognized.map { it.name }}"
                )
            )
        }

        // Process files in severity order: critical -> important -> optional
        val sortedFiles = sortBySeverity(pendingFiles)

        for (file in sortedFiles) {
            val outcome = processFeedbackItem(file, ctx)
            if (outcome is InnerLoopOutcome.Terminate) return outcome
        }

        // Bug guard: no critical/important should remain in pending/
        val remainingBlocking = listPendingFiles(ctx.pendingDir)
            .filter { isBlockingSeverity(it) }
        if (remainingBlocking.isNotEmpty()) {
            return InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(
                    "Critical/important feedback not processed after inner loop: " +
                        remainingBlocking.map { it.name }
                )
            )
        }

        out.info("inner_feedback_loop_completed")
        return InnerLoopOutcome.Continue
    }

    @Suppress("ReturnCount")
    private suspend fun processFeedbackItem(
        file: Path,
        ctx: InnerLoopContext,
    ): InnerLoopOutcome {
        val fileName = file.name
        val isOptional = fileName.startsWith(
            ProtocolVocabulary.SeverityPrefix.OPTIONAL,
        )

        out.info("processing_feedback_item") {
            listOf(Val(fileName, ShepherdValType.FEEDBACK_FILE_NAME))
        }

        // Self-compaction check at done boundary
        val contextState = deps.agentFacade
            .readContextWindowState(ctx.doerHandle)
        out.debug("context_window_state_before_feedback_item") {
            listOf(
                Val(
                    contextState.remainingPercentage?.toString()
                        ?: "unknown",
                    ShepherdValType.CONTEXT_WINDOW_REMAINING,
                )
            )
        }

        // Read feedback file content to include in doer instructions
        val feedbackContent = deps.feedbackFileReader.readContent(file)

        // Assemble and send doer instructions for this single feedback item
        val instructionPath = deps.contextForAgentProvider
            .assembleInstructions(
                buildFeedbackItemRequest(
                    doerConfig = ctx.doerConfig,
                    currentIteration = ctx.currentIteration,
                    feedbackFile = file,
                    feedbackContent = feedbackContent,
                    isOptional = isOptional,
                )
            )

        val reInstructOutcome = deps.reInstructAndAwait
            .execute(ctx.doerHandle, instructionPath.toString())

        return when (reInstructOutcome) {
            is ReInstructOutcome.Crashed ->
                InnerLoopOutcome.Terminate(
                    PartResult.AgentCrashed(reInstructOutcome.details),
                )

            is ReInstructOutcome.FailedWorkflow ->
                InnerLoopOutcome.Terminate(
                    PartResult.FailedWorkflow(reInstructOutcome.reason),
                )

            is ReInstructOutcome.Responded ->
                handleDoerResponse(file, isOptional, ctx)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun handleDoerResponse(
        file: Path,
        isOptional: Boolean,
        ctx: InnerLoopContext,
    ): InnerLoopOutcome {
        // PUBLIC.md validation (shallow)
        val publicMdResult = deps.publicMdValidator.validate(
            ctx.doerConfig.publicMdOutputPath,
            ctx.doerConfig.subPartName,
        )
        if (publicMdResult is PublicMdValidator.ValidationResult.Invalid) {
            return InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(publicMdResult.message),
            )
        }

        // Read resolution marker
        val updatedContent = deps.feedbackFileReader.readContent(file)
        val parseResult = FeedbackResolutionParser.parse(updatedContent)

        return handleResolution(parseResult, file, isOptional, ctx)
    }

    @Suppress("ReturnCount")
    private suspend fun handleResolution(
        parseResult: ParseResult,
        file: Path,
        isOptional: Boolean,
        ctx: InnerLoopContext,
    ): InnerLoopOutcome = when (parseResult) {
        is ParseResult.MissingMarker ->
            InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(
                    "Doer failed to write resolution marker " +
                        "after done signal for ${file.name}"
                )
            )

        is ParseResult.InvalidMarker ->
            InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(
                    "Doer wrote invalid resolution marker " +
                        "'${parseResult.rawValue}' for ${file.name}"
                )
            )

        is ParseResult.Found ->
            handleFoundResolution(parseResult.resolution, file, isOptional, ctx)
    }

    @Suppress("ReturnCount")
    private suspend fun handleFoundResolution(
        resolution: FeedbackResolution,
        file: Path,
        isOptional: Boolean,
        ctx: InnerLoopContext,
    ): InnerLoopOutcome {
        if (resolution == FeedbackResolution.SKIPPED && !isOptional) {
            return InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(
                    "Doer wrote SKIPPED on non-optional " +
                        "feedback item ${file.name}"
                )
            )
        }
        return when (resolution) {
            FeedbackResolution.ADDRESSED -> {
                moveFile(file, ctx.addressedDir)
                commitAfterItem(ctx, "ADDRESSED")
                InnerLoopOutcome.Continue
            }

            FeedbackResolution.SKIPPED -> {
                // Only optional files reach here (guard above)
                moveFile(file, ctx.addressedDir)
                commitAfterItem(ctx, "SKIPPED")
                InnerLoopOutcome.Continue
            }

            FeedbackResolution.REJECTED -> {
                val rejResult = deps.rejectionNegotiationUseCase.execute(
                    doerHandle = ctx.doerHandle,
                    reviewerHandle = ctx.reviewerHandle,
                    feedbackFilePath = file,
                )
                handleRejectionResult(rejResult, file, ctx)
            }
        }
    }

    private suspend fun handleRejectionResult(
        result: RejectionResult,
        file: Path,
        ctx: InnerLoopContext,
    ): InnerLoopOutcome = when (result) {
        is RejectionResult.Accepted -> {
            moveFile(file, ctx.rejectedDir)
            commitAfterItem(ctx, "REJECTED_ACCEPTED")
            InnerLoopOutcome.Continue
        }

        is RejectionResult.AddressedAfterInsistence -> {
            moveFile(file, ctx.addressedDir)
            commitAfterItem(ctx, "ADDRESSED_AFTER_INSISTENCE")
            InnerLoopOutcome.Continue
        }

        is RejectionResult.AgentCrashed ->
            InnerLoopOutcome.Terminate(
                PartResult.AgentCrashed(result.details),
            )

        is RejectionResult.FailedWorkflow ->
            InnerLoopOutcome.Terminate(
                PartResult.FailedWorkflow(result.reason),
            )
    }

    private suspend fun commitAfterItem(
        ctx: InnerLoopContext,
        resolution: String,
    ) {
        deps.gitCommitStrategy.onSubPartDone(
            SubPartDoneContext(
                partName = ctx.doerConfig.partName,
                subPartName = ctx.doerConfig.subPartName,
                subPartRole = ctx.doerConfig.subPartRole.name,
                result = "FEEDBACK_$resolution",
                hasReviewer = true,
                currentIteration = ctx.currentIteration,
                maxIterations = ctx.maxIterations,
                agentType = ctx.doerConfig.agentType,
                model = ctx.doerConfig.model,
            )
        )
    }

    companion object {

        /**
         * Lists all `.md` files in the pending directory, sorted by name.
         */
        fun listPendingFiles(pendingDir: Path): List<Path> {
            if (!Files.exists(pendingDir) || !Files.isDirectory(pendingDir)) {
                return emptyList()
            }
            return Files.list(pendingDir).use { stream ->
                stream
                    .filter {
                        Files.isRegularFile(it) &&
                            it.toString().endsWith(".md")
                    }
                    .sorted()
                    .toList()
            }
        }

        /**
         * Sorts feedback files by severity order:
         * critical -> important -> optional.
         * Within each severity, files are sorted by filename (alphabetical).
         */
        fun sortBySeverity(files: List<Path>): List<Path> {
            val critical = files
                .filter {
                    it.name.startsWith(ProtocolVocabulary.SeverityPrefix.CRITICAL)
                }
                .sorted()
            val important = files
                .filter {
                    it.name.startsWith(ProtocolVocabulary.SeverityPrefix.IMPORTANT)
                }
                .sorted()
            val optional = files
                .filter {
                    it.name.startsWith(ProtocolVocabulary.SeverityPrefix.OPTIONAL)
                }
                .sorted()
            return critical + important + optional
        }

        /**
         * Returns true if the file has a recognized severity prefix
         * (critical__, important__, or optional__).
         */
        fun hasRecognizedSeverityPrefix(file: Path): Boolean {
            val name = file.name
            return name.startsWith(ProtocolVocabulary.SeverityPrefix.CRITICAL) ||
                name.startsWith(ProtocolVocabulary.SeverityPrefix.IMPORTANT) ||
                name.startsWith(ProtocolVocabulary.SeverityPrefix.OPTIONAL)
        }

        /**
         * Returns files that do NOT have a recognized severity prefix.
         * A non-empty result indicates a reviewer bug (e.g., typo in prefix).
         */
        fun findUnrecognizedPrefixFiles(files: List<Path>): List<Path> =
            files.filterNot { hasRecognizedSeverityPrefix(it) }

        /**
         * Returns true if the file has a blocking severity prefix
         * (critical or important).
         */
        fun isBlockingSeverity(file: Path): Boolean {
            val name = file.name
            return name.startsWith(ProtocolVocabulary.SeverityPrefix.CRITICAL) ||
                name.startsWith(ProtocolVocabulary.SeverityPrefix.IMPORTANT)
        }

        /**
         * Moves a file from its current location to the target directory,
         * preserving the filename.
         */
        fun moveFile(source: Path, targetDir: Path) {
            Files.createDirectories(targetDir)
            Files.move(
                source,
                targetDir.resolve(source.fileName),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        /**
         * Builds a [DoerFeedbackItemRequest][com.glassthought.shepherd.core.context.AgentInstructionRequest.DoerFeedbackItemRequest]
         * for a single feedback item.
         *
         * Carries the feedback file content and metadata so that
         * [ContextForAgentProviderImpl] can include the per-item
         * [InstructionSection.FeedbackItem] in the assembled instructions.
         */
        fun buildFeedbackItemRequest(
            doerConfig: SubPartConfig,
            currentIteration: Int,
            feedbackFile: Path,
            feedbackContent: String,
            isOptional: Boolean,
        ) = com.glassthought.shepherd.core.context.AgentInstructionRequest
            .DoerFeedbackItemRequest(
                roleDefinition = doerConfig.roleDefinition,
                ticketContent = doerConfig.ticketContent,
                iterationNumber = currentIteration,
                outputDir = doerConfig.outputDir,
                publicMdOutputPath = doerConfig.publicMdOutputPath,
                subPartName = doerConfig.subPartName,
                executionContext = doerConfig.executionContext,
                feedbackItem = com.glassthought.shepherd.core.context.InstructionSection.FeedbackItem(
                    feedbackContent = feedbackContent,
                    currentPath = feedbackFile,
                    isOptional = isOptional,
                ),
            )
    }
}
