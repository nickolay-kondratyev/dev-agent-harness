package com.glassthought.chainsaw.core.supporting.ticket

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

/**
 * Result of splitting a markdown document into its YAML frontmatter and body.
 *
 * @param yamlFields parsed YAML key->value map; keys and values are both [String].
 *   All scalar values (dates, integers, booleans, etc.) are preserved as their original
 *   string representation from the source document. snakeyaml's type resolution for scalars
 *   is suppressed to prevent leaking internal types (e.g. java.util.Date) to callers.
 *   Non-scalar values (sequences, nested maps) are coerced via [Any.toString].
 * @param body the markdown body text after the closing --- delimiter (trimmed of leading newline).
 */
data class FrontmatterParseResult(
    val yamlFields: Map<String, String>,
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
     * snakeyaml [Resolver] that treats ALL scalars as plain strings.
     *
     * By default, snakeyaml resolves unquoted scalars to their inferred types:
     * ISO timestamps become java.util.Date, integers become Int, booleans become Boolean, etc.
     * Overriding [addImplicitResolvers] to register no implicit type resolvers forces every
     * scalar to remain a String — its original textual value from the YAML source.
     *
     * Note: This only affects scalar nodes. YAML sequences and mappings remain as
     * List/Map, and are subsequently coerced to String via [Any.toString] in [parse].
     */
    private class StringOnlyResolver : Resolver() {
        override fun addImplicitResolvers() {
            // Intentionally register no implicit resolvers — all scalars stay as strings.
        }
    }

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

        // StringOnlyResolver prevents snakeyaml from resolving scalar values (ISO timestamps,
        // integers, booleans) into their inferred Java types — they remain plain Strings.
        // Yaml instances are NOT thread-safe; create a new one per call.
        val dumperOptions = DumperOptions()
        val yaml = Yaml(
            SafeConstructor(LoaderOptions()),
            Representer(dumperOptions),
            dumperOptions,
            StringOnlyResolver(),
        )
        val rawParsed: Any? = yaml.load<Any>(yamlBlock)

        require(rawParsed != null && rawParsed is Map<*, *>) {
            "Frontmatter is not a YAML mapping"
        }

        require(rawParsed.keys.all { it is String }) {
            "frontmatter_yaml_keys_must_be_strings"
        }

        // Non-scalar values (YAML sequences, nested maps) are coerced to String via toString().
        // Scalar values are already String thanks to StringOnlyResolver — toString() is a no-op for them.
        @Suppress("UNCHECKED_CAST")
        val yamlFields: Map<String, String> = (rawParsed as Map<String, Any>)
            .mapValues { (_, v) -> v.toString() }

        // Join body lines and strip any leading blank line that follows the closing ---.
        val body = bodyLines.joinToString("\n").trimStart('\n')

        return FrontmatterParseResult(yamlFields = yamlFields, body = body)
    }
}
