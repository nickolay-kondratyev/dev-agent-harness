package com.glassthought.shepherd.usecase.healthmonitoring

import com.glassthought.shepherd.core.state.PartResult
import java.nio.file.Path

/**
 * Records structured failure context for the benefit of the next retry attempt.
 *
 * See ref.ap.cI3odkAZACqDst82HtxKa.E for the full spec.
 */
fun interface TicketFailureLearningUseCase {
    suspend fun recordFailureLearning(partResult: PartResult)
}

/** No-op stub — used when failure learning is disabled or unnecessary. */
class NoOpTicketFailureLearningUseCase : TicketFailureLearningUseCase {
    override suspend fun recordFailureLearning(partResult: PartResult) {
        // No-op: caller does not need failure learning.
    }
}

/**
 * Run-level context for failure learning, injected as a constructor dependency.
 *
 * Captures everything about the current try that the impl needs to build
 * the TRY-N section and perform git operations.
 */
data class FailureLearningRunContext(
    /** Path to the ticket markdown file. */
    val ticketPath: String,

    /** Current try number (e.g., 1 for try-1). */
    val tryNumber: Int,

    /** The try branch name (e.g., nid_abc__dashboard__try-1). */
    val branchName: String,

    /** The branch from which the try branch was created. */
    val originatingBranch: String,

    /** Path to the .ai_out/ directory for this try. */
    val aiOutDir: String,

    /** Working directory for git and agent operations. */
    val workingDirectory: Path,

    /** Workflow type (e.g., "with-planning", "straightforward"). */
    val workflowType: String,

    /** Which part failed (e.g., "backend_impl/impl"). */
    val failedAt: String,

    /** Current iteration at time of failure (e.g., "1/3"). */
    val iteration: String,

    /** Parts that completed successfully before the failure. */
    val partsCompleted: List<String>,
)

/**
 * Structured failure context extracted from a [PartResult].
 *
 * Internal to the impl — used to assemble agent instructions and the TRY-N section.
 */
data class PartResultFailureContext(
    /** Workflow type (e.g., "with-planning", "straightforward"). */
    val workflowType: String,

    /** Failure type (e.g., "FailedWorkflow", "AgentCrashed", "FailedToConverge"). */
    val failureType: String,

    /** Which part failed (e.g., "backend_impl/impl"). */
    val failedAt: String,

    /** Current iteration at time of failure (e.g., "1/3"). */
    val iteration: String,

    /** Parts that completed successfully before the failure. */
    val partsCompleted: List<String>,
)
