package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRequest
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentResult
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.state.PartResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Records structured failure learning by running a non-interactive agent to analyze
 * .ai_out/ artifacts, then appending a TRY-N section to the ticket file.
 *
 * See ref.ap.cI3odkAZACqDst82HtxKa.E for the full spec.
 *
 * **Never throws** — all errors are logged as WARN and swallowed.
 * Learning is best-effort; it must never block or fail the main workflow.
 */
class TicketFailureLearningUseCaseImpl(
    outFactory: OutFactory,
    private val nonInteractiveAgentRunner: NonInteractiveAgentRunner,
    private val runContext: FailureLearningRunContext,
    private val processRunner: ProcessRunner,
) : TicketFailureLearningUseCase {

    private val out = outFactory.getOutForClass(TicketFailureLearningUseCaseImpl::class)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun recordFailureLearning(partResult: PartResult) {
        try {
            doRecordFailureLearning(partResult)
        } catch (e: Exception) {
            out.warn(
                "ticket_failure_learning_failed_entirely",
                Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
            )
        }
    }

    private suspend fun doRecordFailureLearning(partResult: PartResult) {
        val failureContext = buildFailureContext(partResult)
        val agentSummary = runAnalysisAgent(failureContext)
        val trySection = buildTrySection(failureContext, agentSummary)

        appendTrySectionToTicket(trySection)
        commitOnTryBranch()
        propagateToOriginatingBranch()
    }

    // ── PartResult → FailureContext building ─────────────────────────────────

    internal fun buildFailureContext(partResult: PartResult): PartResultFailureContext {
        val failureType = when (partResult) {
            is PartResult.FailedWorkflow -> "FailedWorkflow"
            is PartResult.AgentCrashed -> "AgentCrashed"
            is PartResult.FailedToConverge -> "FailedToConverge"
            is PartResult.Completed -> "Completed"
        }

        return PartResultFailureContext(
            workflowType = runContext.workflowType,
            failureType = failureType,
            failedAt = runContext.failedAt,
            iteration = runContext.iteration,
            partsCompleted = runContext.partsCompleted,
        )
    }

    // ── Agent invocation ────────────────────────────────────────────────────

    private suspend fun runAnalysisAgent(failureContext: PartResultFailureContext): String? {
        val instructions = assembleAgentInstructions(failureContext)

        val request = NonInteractiveAgentRequest(
            instructions = instructions,
            workingDirectory = runContext.workingDirectory,
            agentType = AgentType.CLAUDE_CODE,
            model = AGENT_MODEL,
            timeout = AGENT_TIMEOUT,
        )

        return when (val result = nonInteractiveAgentRunner.run(request)) {
            is NonInteractiveAgentResult.Success -> result.output
            is NonInteractiveAgentResult.Failed -> {
                out.warn(
                    "failure_learning_agent_failed",
                    Val(result.exitCode, ValType.COUNT),
                    Val(result.output, ValType.STRING_USER_AGNOSTIC),
                )
                null
            }
            is NonInteractiveAgentResult.TimedOut -> {
                out.warn("failure_learning_agent_timed_out")
                null
            }
        }
    }

    internal fun assembleAgentInstructions(failureContext: PartResultFailureContext): String {
        return buildString {
            appendLine("You are analyzing a failed shepherd run to help the next retry succeed.")
            appendLine()
            appendLine("## Failure Facts")
            appendLine("- Try number: ${runContext.tryNumber}")
            appendLine("- Branch: ${runContext.branchName}")
            appendLine("- Workflow: ${failureContext.workflowType}")
            appendLine("- Failure type: ${failureContext.failureType}")
            appendLine("- Failed at: ${failureContext.failedAt} (iteration ${failureContext.iteration})")
            appendLine("- Parts completed: ${failureContext.partsCompleted.joinToString(", ").ifEmpty { "none" }}")
            appendLine()
            appendLine("## Artifacts Directory")
            appendLine("Read the artifacts in: ${runContext.aiOutDir}")
            appendLine("- Look at current_state.json for workflow progress")
            appendLine("- Read all PUBLIC.md files for agent outputs")
            appendLine("- Read PLAN.md (under shared/plan/) if present")
            appendLine()
            appendLine("## Expected Output Format")
            appendLine("Output ONLY the following to stdout (no other text):")
            appendLine()
            appendLine("**Approach**: {what was attempted}")
            appendLine("**Root Cause**: {why it failed}")
            appendLine("**Recommendations**: {what the next try should do differently}")
        }
    }

    // ── TRY-N section building ──────────────────────────────────────────────

    internal fun buildTrySection(
        failureContext: PartResultFailureContext,
        agentSummary: String?,
    ): String {
        val partsCompletedText = failureContext.partsCompleted.joinToString(", ").ifEmpty { "none" }
        val summaryText = agentSummary ?: FALLBACK_SUMMARY

        return buildString {
            appendLine("### TRY-${runContext.tryNumber}")
            appendLine()
            appendLine("- **Branch**: `${runContext.branchName}`")
            appendLine("- **Workflow**: ${failureContext.workflowType}")
            appendLine("- **Failure type**: ${failureContext.failureType}")
            appendLine("- **Failed at**: ${failureContext.failedAt} (iteration ${failureContext.iteration})")
            appendLine("- **Parts completed**: $partsCompletedText")
            appendLine()
            appendLine("#### Summary")
            appendLine()
            append(summaryText)
        }
    }

    // ── Ticket file mutation ────────────────────────────────────────────────

    internal fun appendTrySectionToTicket(trySection: String) {
        val ticketFile = Path.of(runContext.ticketPath)
        val existingContent = Files.readString(ticketFile)

        val updatedContent = if (existingContent.contains(PREVIOUS_FAILED_ATTEMPTS_HEADING)) {
            // Append under existing heading
            "$existingContent\n$trySection\n"
        } else {
            // Add heading then section
            "$existingContent\n$PREVIOUS_FAILED_ATTEMPTS_HEADING\n\n$trySection\n"
        }

        Files.writeString(ticketFile, updatedContent)
    }

    // ── Git operations ──────────────────────────────────────────────────────

    @Suppress("TooGenericExceptionCaught")
    private suspend fun commitOnTryBranch() {
        try {
            processRunner.runProcess(
                "git", "-C", runContext.workingDirectory.toString(),
                "add", runContext.ticketPath,
            )
            processRunner.runProcess(
                "git", "-C", runContext.workingDirectory.toString(),
                "commit", "-m", "[shepherd] ticket-failure-learning — TRY-${runContext.tryNumber}",
            )
        } catch (e: Exception) {
            out.warn(
                "failure_learning_git_commit_failed",
                Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun propagateToOriginatingBranch() {
        try {
            val workDir = runContext.workingDirectory.toString()
            processRunner.runProcess(
                "git", "-C", workDir, "checkout", runContext.originatingBranch,
            )
            processRunner.runProcess(
                "git", "-C", workDir, "checkout", runContext.branchName, "--", runContext.ticketPath,
            )
            processRunner.runProcess(
                "git", "-C", workDir,
                "commit", "-m",
                "[shepherd] ticket-failure-learning — TRY-${runContext.tryNumber} (propagated)",
            )
            processRunner.runProcess(
                "git", "-C", workDir, "checkout", runContext.branchName,
            )
        } catch (e: Exception) {
            out.warn(
                "failure_learning_propagation_failed",
                Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
            )
            // Best-effort: try to return to the try branch even if propagation failed.
            // WHY-NOT: We do not clean up staged changes on the originating branch here.
            // If failure occurs between checkout and commit, the originating branch may be
            // left with a staged ticket file. This is acceptable for V1 given the best-effort
            // contract — the try branch (our primary target) is unaffected.
            tryCheckoutBranch(runContext.branchName)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryCheckoutBranch(branchName: String) {
        try {
            processRunner.runProcess(
                "git", "-C", runContext.workingDirectory.toString(),
                "checkout", branchName,
            )
        } catch (e: Exception) {
            out.warn(
                "failure_learning_checkout_recovery_failed",
                Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
            )
        }
    }

    companion object {
        private const val AGENT_MODEL = "sonnet"
        private val AGENT_TIMEOUT = 20.minutes
        internal const val PREVIOUS_FAILED_ATTEMPTS_HEADING = "## Previous Failed Attempts"
        internal const val FALLBACK_SUMMARY =
            "*Agent failed to produce summary — structured facts above are the only record for this try.*"
    }
}
