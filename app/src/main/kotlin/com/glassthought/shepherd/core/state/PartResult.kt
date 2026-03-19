package com.glassthought.shepherd.core.state

/**
 * Outcome of executing a single plan part (all its sub-parts).
 *
 * Returned by PartExecutorImpl after the part's execution loop terminates.
 * See ref.ap.fFr7GUmCYQEV5SJi8p6AS.E for how the executor maps [AgentSignal] to [PartResult].
 */
sealed class PartResult {

    /** All sub-parts completed successfully. */
    object Completed : PartResult()

    /** Agent called /callback-shepherd/signal/fail-workflow. */
    data class FailedWorkflow(val reason: String) : PartResult()

    /** Reviewer sent needs_iteration beyond iteration.max and user chose to abort. */
    data class FailedToConverge(val summary: String) : PartResult()

    /** Agent crashed — V1: hard stop (no automatic recovery). */
    data class AgentCrashed(val details: String) : PartResult()
}
