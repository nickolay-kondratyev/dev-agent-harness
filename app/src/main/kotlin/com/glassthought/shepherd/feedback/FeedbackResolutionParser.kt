package com.glassthought.shepherd.feedback

/**
 * Valid resolution states a doer can assign to a feedback item.
 */
enum class FeedbackResolution {
    ADDRESSED,
    REJECTED,
    SKIPPED,
}

/**
 * Result of parsing a `## Resolution:` marker from feedback file content.
 *
 * - [Found] — a valid resolution keyword was present.
 * - [MissingMarker] — no `## Resolution:` line found in the content.
 * - [InvalidMarker] — a `## Resolution:` line was found but the keyword is unrecognized.
 */
sealed class ParseResult {
    data class Found(val resolution: FeedbackResolution) : ParseResult()
    object MissingMarker : ParseResult()
    data class InvalidMarker(val rawValue: String) : ParseResult()
}

/**
 * Stateless parser that extracts the `## Resolution:` marker from feedback file content.
 *
 * Scans for a line matching `## Resolution: <KEYWORD>` (case-insensitive keyword match).
 * Does NOT validate reasoning content or whether SKIPPED is valid for the file's severity
 * — that is the caller's responsibility.
 *
 * Part of the Granular Feedback Loop feature (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).
 */
class FeedbackResolutionParser {

    companion object {
        // [## Resolution:] header pattern — matches the markdown H2 marker followed by optional keyword.
        // Group 1 captures everything after the colon (may be empty/blank).
        private val RESOLUTION_LINE_REGEX = Regex("""^##\s+Resolution:\s*(.*)$""", RegexOption.MULTILINE)
    }

    /**
     * Parses the `## Resolution:` marker from [fileContent].
     *
     * @return [ParseResult.Found] when a valid keyword (ADDRESSED, REJECTED, SKIPPED) is present,
     *   [ParseResult.MissingMarker] when no `## Resolution:` line exists,
     *   [ParseResult.InvalidMarker] when the keyword after the colon is unrecognized.
     */
    fun parse(fileContent: String): ParseResult {
        val rawKeyword = RESOLUTION_LINE_REGEX.find(fileContent)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return ParseResult.MissingMarker

        val resolution = FeedbackResolution.entries.firstOrNull {
            it.name.equals(rawKeyword, ignoreCase = true)
        }

        return if (resolution != null) {
            ParseResult.Found(resolution)
        } else {
            ParseResult.InvalidMarker(rawKeyword)
        }
    }
}
