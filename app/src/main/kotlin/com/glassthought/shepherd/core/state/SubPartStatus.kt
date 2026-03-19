package com.glassthought.shepherd.core.state

/**
 * Lifecycle status of a single sub-part (doer or reviewer) within a plan part.
 *
 * State machine is strictly forward-only — no back-transitions.
 * [COMPLETED] and [FAILED] are terminal states.
 *
 * See [SubPartStateTransition] for the exhaustive set of legal transitions.
 */
enum class SubPartStatus {
    /** Initial state — sub-part has not been attempted. */
    NOT_STARTED,

    /** Agent has been spawned and is working. */
    IN_PROGRESS,

    /** Part completed successfully — doer-only done, or reviewer "pass". */
    COMPLETED,

    /** Unrecoverable failure (fail-workflow, crash, failed-to-converge). */
    FAILED,
}
