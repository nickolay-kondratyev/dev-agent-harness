package com.glassthought.chainsaw.core.supporting.git

import com.glassthought.chainsaw.core.supporting.ticket.TicketData

/**
 * Stateless utility for building git branch names from [TicketData].
 *
 * Branch name format: `{ticketId}__{slugified_title}__try-{N}`
 *
 * The slug portion is capped at [MAX_SLUG_LENGTH] characters. The full branch name
 * may exceed that limit due to the ticket ID and try-N suffix.
 *
 * ref.ap.THL21SyZzJhzInG2m4zl2.E — See "Git Branch / Feature Naming" in design ticket.
 */
object BranchNameBuilder {

    private const val DELIMITER = "__"
    private const val MAX_SLUG_LENGTH = 60
    private const val TRY_PREFIX = "try-"
    private const val UNTITLED_FALLBACK = "untitled"

    /**
     * Builds a branch name from the given [ticketData] and [tryNumber].
     *
     * @param ticketData The ticket to derive the branch name from.
     * @param tryNumber The attempt number (must be >= 1).
     * @return A branch name in the format `{id}__{slug}__try-{N}`.
     * @throws IllegalArgumentException if [tryNumber] < 1 or [ticketData].id is blank.
     */
    fun build(ticketData: TicketData, tryNumber: Int): String {
        require(tryNumber >= 1) { "tryNumber must be >= 1, got [$tryNumber]" }
        require(ticketData.id.isNotBlank()) { "ticketData.id must not be blank" }

        val slug = slugify(ticketData.title)
        return "${ticketData.id}${DELIMITER}${slug}${DELIMITER}${TRY_PREFIX}${tryNumber}"
    }

    /**
     * Converts a title into a URL/git-safe slug.
     *
     * Algorithm:
     * 1. Lowercase
     * 2. Replace non-alphanumeric/non-hyphen characters with hyphens
     * 3. Collapse consecutive hyphens
     * 4. Trim leading/trailing hyphens
     * 5. Truncate to [MAX_SLUG_LENGTH]
     * 6. Trim trailing hyphen introduced by truncation
     * 7. Fall back to "untitled" if result is empty
     *
     * Visible as `internal` for direct testing of edge cases.
     */
    fun slugify(title: String): String {
        val result = title
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .take(MAX_SLUG_LENGTH)
            .trimEnd('-')

        return result.ifEmpty { UNTITLED_FALLBACK }
    }
}
