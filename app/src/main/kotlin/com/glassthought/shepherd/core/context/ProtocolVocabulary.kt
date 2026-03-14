package com.glassthought.shepherd.core.context

/**
 * Central registry of all protocol keywords used in agent↔harness communication.
 *
 * These constants are referenced by:
 * - [InstructionSections] — embedded in static instruction text via string templates
 * - Keyword presence tests — asserted via `shouldContain` to verify instructions include
 *   all required protocol terms without brittle exact-match testing
 *
 * If a keyword changes here, both instruction text and test assertions update automatically
 * (compile-time link). If a keyword is removed from instruction text, keyword tests catch it.
 *
 * See agent-to-server-communication-protocol.md (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E)
 * and granular-feedback-loop.md (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).
 */
object ProtocolVocabulary {

    /**
     * Feedback item lifecycle statuses.
     *
     * See `__feedback/` directory spec (ref.ap.3Hskx3JzhDlixTnvYxclk.E).
     */
    object FeedbackStatus {
        const val UNADDRESSED = "unaddressed"
        const val ADDRESSED = "addressed"
        const val REJECTED = "rejected"
    }

    /**
     * Feedback item severity levels — determines processing order and blocking behavior.
     *
     * See granular-feedback-loop.md (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E), decision D4.
     */
    object Severity {
        const val CRITICAL = "critical"
        const val IMPORTANT = "important"
        const val OPTIONAL = "optional"
    }

    /**
     * Result values sent with the `done` signal.
     *
     * See AgentSignal spec (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E).
     */
    object DoneResult {
        const val COMPLETED = "completed"
        const val PASS = "pass"
        const val NEEDS_ITERATION = "needs_iteration"
    }

    /**
     * Signal endpoint names (fire-and-forget).
     *
     * See agent-to-server-communication-protocol.md (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E).
     */
    object Signal {
        const val DONE = "done"
        const val FAIL_WORKFLOW = "fail-workflow"
        const val ACK_PAYLOAD = "ack-payload"
        const val USER_QUESTION = "user-question"
        const val PING_ACK = "ping-ack"
    }

    /** WHY-NOT comment protocol marker. See ref.ap.kmiKk7vECiNSpJjAXYMyE.E. */
    const val WHY_NOT = "WHY-NOT"

    /** Movement log section header in feedback files. See ref.ap.3Hskx3JzhDlixTnvYxclk.E. */
    const val MOVEMENT_LOG = "Movement Log"

    /** Callback shell script for fire-and-forget signals. */
    const val CALLBACK_SIGNAL_SCRIPT = "callback_shepherd.signal.sh"

    /** Callback shell script for synchronous queries. */
    const val CALLBACK_QUERY_SCRIPT = "callback_shepherd.query.sh"

    /** Payload delivery ACK XML tag name. See ref.ap.r0us6iYsIRzrqHA5MVO0Q.E. */
    const val PAYLOAD_ACK_TAG = "payload_from_shepherd_must_ack"
}
