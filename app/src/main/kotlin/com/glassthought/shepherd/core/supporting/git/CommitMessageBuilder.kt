package com.glassthought.shepherd.core.supporting.git

/**
 * Stateless utility for building git commit messages.
 *
 * Format: `[shepherd] {part_name}/{sub_part_name} — {result} (iteration {N}/{max})`
 *
 * Iteration info is included only when the part has a reviewer.
 * See doc/core/git.md — "Commit Message Convention" (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).
 */
object CommitMessageBuilder {

    private const val PREFIX = "[shepherd]"
    private const val EM_DASH = "—"

    /**
     * Builds a commit message from the given parameters.
     *
     * @param partName name of the part (e.g., "planning", "ui_design")
     * @param subPartName name of the sub-part (e.g., "impl", "review", "plan")
     * @param result outcome (e.g., "completed", "pass", "needs_iteration")
     * @param hasReviewer whether the part has a reviewer (determines iteration suffix)
     * @param currentIteration current iteration number (only used when [hasReviewer] is true)
     * @param maxIterations max iterations (only used when [hasReviewer] is true)
     * @return formatted commit message
     * @throws IllegalArgumentException if inputs are invalid
     */
    fun build(
        partName: String,
        subPartName: String,
        result: String,
        hasReviewer: Boolean,
        currentIteration: Int = 0,
        maxIterations: Int = 0,
    ): String {
        require(partName.isNotBlank()) { "partName must not be blank" }
        require(subPartName.isNotBlank()) { "subPartName must not be blank" }
        require(result.isNotBlank()) { "result must not be blank" }

        if (hasReviewer) {
            require(currentIteration >= 1) {
                "currentIteration must be >= 1 when hasReviewer is true, got [$currentIteration]"
            }
            require(maxIterations >= 1) {
                "maxIterations must be >= 1 when hasReviewer is true, got [$maxIterations]"
            }
        }

        val base = "$PREFIX $partName/$subPartName $EM_DASH $result"

        return if (hasReviewer) {
            "$base (iteration $currentIteration/$maxIterations)"
        } else {
            base
        }
    }
}
