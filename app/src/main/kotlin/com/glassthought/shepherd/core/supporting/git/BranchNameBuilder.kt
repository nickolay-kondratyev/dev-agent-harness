package com.glassthought.shepherd.core.supporting.git

import com.glassthought.shepherd.core.supporting.ticket.TicketData
import java.security.MessageDigest

/**
 * Stateless utility for building git branch names from [TicketData].
 *
 * Branch name format: `{ticketId}__{slugified_title}__try-{N}`
 *
 * The slug portion is capped at [MAX_SLUG_LENGTH] characters. When the full slug exceeds
 * this limit, it is truncated at word boundaries and a 6-char SHA-1 hash suffix is appended
 * for uniqueness (see [Slug Truncation](doc/core/git.md#slug-truncation)).
 *
 * ref.ap.THL21SyZzJhzInG2m4zl2.E — See "Git Branch / Feature Naming" in design ticket.
 */
object BranchNameBuilder {

    private const val DELIMITER = "__"
    private const val MAX_SLUG_LENGTH = 50
    private const val HASH_HEX_CHAR_COUNT = 6
    private const val HASH_BYTE_COUNT = HASH_HEX_CHAR_COUNT / 2 // 3 bytes = 6 hex chars
    private const val HASH_SUFFIX_LENGTH = 1 + HASH_HEX_CHAR_COUNT // 1 hyphen + 6 hex chars = 7
    private const val MAX_WORD_BUDGET = MAX_SLUG_LENGTH - HASH_SUFFIX_LENGTH // 43
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
     * 5. If length <= [MAX_SLUG_LENGTH]: use as-is
     * 6. If longer: truncate at word boundaries within [MAX_WORD_BUDGET] chars,
     *    append `-{hash6}` where hash6 = first 6 hex chars of SHA-1 of the full slug
     * 7. Fall back to "untitled" if result is empty
     *
     * Visible as `internal` for direct testing of edge cases.
     */
    fun slugify(title: String): String {
        val fullSlug = title
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-')

        return when {
            fullSlug.isEmpty() -> UNTITLED_FALLBACK
            fullSlug.length <= MAX_SLUG_LENGTH -> fullSlug
            else -> truncateWithHash(fullSlug)
        }
    }

    /**
     * Truncates a slug that exceeds [MAX_SLUG_LENGTH] by preserving whole hyphen-delimited
     * words within [MAX_WORD_BUDGET] chars, then appending a 6-char SHA-1 hash suffix.
     *
     * Guarantees: result length <= [MAX_SLUG_LENGTH], never ends with a hyphen.
     */
    private fun truncateWithHash(fullSlug: String): String {
        val wordPrefix = buildWordPrefix(fullSlug)
        val hash6 = sha1Hash6(fullSlug)
        return "${wordPrefix}-${hash6}"
    }

    /**
     * Accumulates whole hyphen-delimited words from [fullSlug] while the total
     * length stays within [MAX_WORD_BUDGET].
     *
     * If the first word alone exceeds the budget, falls back to character-level
     * truncation of the first [MAX_WORD_BUDGET] characters.
     */
    private fun buildWordPrefix(fullSlug: String): String {
        val words = fullSlug.split("-")

        if (words.first().length > MAX_WORD_BUDGET) {
            return fullSlug.take(MAX_WORD_BUDGET).trimEnd('-')
        }

        var result = words.first()
        for (word in words.drop(1)) {
            val candidate = "$result-$word"
            if (candidate.length > MAX_WORD_BUDGET) break
            result = candidate
        }
        return result
    }

    /**
     * Returns the first 6 hex characters of the SHA-1 hash of [input].
     */
    private fun sha1Hash6(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.take(HASH_BYTE_COUNT).joinToString("") { "%02x".format(it) }
    }
}
