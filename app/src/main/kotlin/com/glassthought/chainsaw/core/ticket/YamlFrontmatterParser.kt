package com.glassthought.chainsaw.core.ticket

import org.yaml.snakeyaml.Yaml

/**
 * Result of splitting a markdown document into its YAML frontmatter and body.
 *
 * @param yamlFields parsed YAML key->value map; keys are String, values are Any (snakeyaml output).
 * @param body the markdown body text after the closing --- delimiter (trimmed of leading newline).
 */
data class FrontmatterParseResult(
    val yamlFields: Map<String, Any>,
    val body: String,
)

/**
 * Stateless utility that parses a markdown string with YAML frontmatter into fields and body.
 *
 * Reused by both the ticket parser and the role catalog loader — any markdown file using
 * the standard YAML frontmatter pattern (--- delimiters) can be parsed here.
 */
object YamlFrontmatterParser {

    private const val DELIMITER = "---"

    /**
     * Parses a markdown string with YAML frontmatter into fields and body.
     *
     * The content must start with a line containing only "---" (the opening delimiter),
     * followed by the YAML block, followed by another line containing only "---" (the closing
     * delimiter). Everything after the closing delimiter is the body.
     *
     * @param content Full markdown file content as a string.
     * @throws IllegalArgumentException if frontmatter delimiters are missing or YAML is invalid.
     */
    fun parse(content: String): FrontmatterParseResult {
        val lines = content.lines()

        require(lines.firstOrNull()?.trimEnd() == DELIMITER) {
            "Content does not start with YAML frontmatter delimiter (---)"
        }

        // Search for the closing "---" in the remaining lines (drop the opening delimiter).
        // indexOfFirst returns the index relative to lines.drop(1).
        val closingIndexRelative = lines.drop(1).indexOfFirst { it.trimEnd() == DELIMITER }

        require(closingIndexRelative >= 0) {
            "Missing closing YAML frontmatter delimiter (---)"
        }

        // Actual index in the full lines list is closingIndexRelative + 1.
        val closingIndex = closingIndexRelative + 1
        val yamlLines = lines.subList(1, closingIndex)
        // Everything after the closing "---" line is the body.
        val bodyLines = lines.drop(closingIndex + 1)

        val yamlBlock = yamlLines.joinToString("\n")

        // snakeyaml Yaml() instances are NOT thread-safe; create a new one per call.
        val rawParsed: Any? = Yaml().load(yamlBlock)

        require(rawParsed != null && rawParsed is Map<*, *>) {
            "Frontmatter is not a YAML mapping"
        }

        require(rawParsed.keys.all { it is String }) {
            "frontmatter_yaml_keys_must_be_strings"
        }

        @Suppress("UNCHECKED_CAST")
        val yamlFields = rawParsed as Map<String, Any>

        // Join body lines and strip any leading blank line that follows the closing ---.
        val body = bodyLines.joinToString("\n").trimStart('\n')

        return FrontmatterParseResult(yamlFields = yamlFields, body = body)
    }
}
