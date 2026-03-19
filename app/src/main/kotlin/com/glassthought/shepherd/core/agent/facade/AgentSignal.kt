package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.annotation.AnchorPoint

/**
 * Sealed class representing signals that flow from an agent (via the server or health monitoring)
 * back to the orchestration layer as the return value of
 * [AgentFacade.sendPayloadAndAwaitSignal]/ref.ap.1aEIkOGUeTijwvrACf3Ga.E.
 *
 * [Done], [FailWorkflow], and [SelfCompacted] are completed by the server via HTTP callbacks.
 * [Crashed] is produced by the facade's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E)
 * inside `sendPayloadAndAwaitSignal`.
 *
 * See PartExecutor spec ref.ap.fFr7GUmCYQEV5SJi8p6AS.E for how the executor maps these signals
 * to [PartResult].
 */
@AnchorPoint("ap.uPdI6LlYO56c1kB5W0dpE.E")
sealed class AgentSignal {

    /**
     * Agent called `/callback-shepherd/signal/done` with a valid [DoneResult].
     *
     * The [result] indicates whether the agent completed its work ([DoneResult.COMPLETED]),
     * approved the work as a reviewer ([DoneResult.PASS]), or requests another iteration
     * ([DoneResult.NEEDS_ITERATION]).
     */
    data class Done(val result: DoneResult) : AgentSignal()

    /**
     * Agent called `/callback-shepherd/signal/fail-workflow` — the agent determined that the
     * overall ticket cannot be completed and explicitly requested workflow failure.
     *
     * The [reason] contains the agent's explanation of why the workflow should fail.
     */
    data class FailWorkflow(val reason: String) : AgentSignal()

    /**
     * The facade's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) determined
     * the agent has crashed — no activity after a health ping within the configured timeout.
     *
     * The [details] contain diagnostic information about the crash detection
     * (e.g., session name, last activity age, detection context).
     */
    data class Crashed(val details: String) : AgentSignal()

    /**
     * Agent completed self-compaction (ref.ap.HU6KB4uRDmOObD54gdjYs.E) — the agent rotated
     * its own session to free context window space.
     *
     * The executor should re-register with the new session and continue the current work cycle.
     */
    object SelfCompacted : AgentSignal()
}

/**
 * Result variants for [AgentSignal.Done].
 *
 * Maps to the `result` field in the `/callback-shepherd/signal/done` HTTP callback.
 */
enum class DoneResult {
    /** Doer finished this round's work. */
    COMPLETED,

    /** Reviewer approves the doer's work — part is complete. */
    PASS,

    /** Reviewer requests changes — triggers another doer iteration. */
    NEEDS_ITERATION,
}
