---
verdict: READY_WITH_NOTES
branch: CC_nid_r9on08uqjmumuc6wi2c53e8p9_E__ticket-parser_sonnet-v4.6
reviewed_at: 2026-03-10
---

## Summary

Implements the `TicketParser` component in `com.glassthought.chainsaw.core.ticket`:

- `YamlFrontmatterParser` (object) — splits markdown content into YAML frontmatter fields and body. Uses snakeyaml with a custom `StringOnlyResolver` to prevent type leakage (dates, booleans, integers all remain strings).
- `FrontmatterParseResult` (data class) — result carrier for the above.
- `TicketData` (data class) — structured representation of a parsed ticket.
- `TicketParserImpl` / `TicketParser` (interface+impl) — reads file from disk, delegates to `YamlFrontmatterParser`, enforces `id`+`title` required fields.
- 27 unit tests (10 `YamlFrontmatterParserTest` + 14 `TicketParserTest` + 3 from the non-string-id split) covering the main happy path and error cases.

Sanity check passes. Tests pass.

Overall: solid, well-structured implementation. The `StringOnlyResolver` approach to prevent snakeyaml type leakage is the right call and is well-explained. Architecture is clean (stateless utility, interface+impl, DI via constructor). Issues below are maintainability concerns for the future — not blocking.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `trimStart('\n')` silently drops multiple leading blank lines from body

File: `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParser.kt`, line 112.

```kotlin
val body = bodyLines.joinToString("\n").trimStart('\n')
```

`trimStart('\n')` strips ALL leading newline characters, not just the single blank line that conventionally follows the closing `---`. If a ticket body legitimately begins with multiple blank lines (e.g., for intentional markdown spacing), those lines are silently eaten.

The code intends to strip one leading blank line. Using `trimStart` strips arbitrarily many. This is a silent, lossy transformation with no test coverage for the case where the body begins with two or more blank lines.

**Recommended fix:** Drop exactly the lines before the first non-blank line, or simply use `dropWhile { it.isBlank() }` on `bodyLines` and then rejoin. Or if the intent is only to drop the one blank line immediately after `---`, use `bodyLines.drop(1)` conditionally: if `bodyLines.firstOrNull()?.isBlank() == true`.

### 2. YAML `null` scalar becomes the string `"null"` instead of Kotlin `null`

Files: `YamlFrontmatterParser.kt`, `TicketParser.kt`, `TicketData.kt`.

With `StringOnlyResolver` suppressing all implicit resolvers, the YAML keyword `null` (unquoted) is not resolved to the null type tag. It is instead treated as a plain string, so:

```yaml
status: null
```

produces `yamlFields["status"] = "null"` (the string `"null"`), not an absent key. This means `TicketData.status` would be `"null"` rather than Kotlin `null`.

`TicketData.status` is declared `String?` suggesting callers may test `status == null` to detect a missing status. For a ticket with `status: null` in its frontmatter, the caller would get `status = "null"` (truthy string), not `null` — a semantic mismatch.

Real ticket files use explicit strings (`open`, `closed`, `in_progress`), so this is unlikely to bite today. But it is a latent correctness hazard once `status` is consumed downstream. There is no test covering this case.

**Recommended fix:** After `mapValues`, add a post-processing step that converts the string value `"null"` to a genuine absent entry, or document explicitly that `null` YAML values are not supported and throw `IllegalArgumentException` if encountered. At minimum, add a test that asserts the behavior.

### 3. Non-string YAML key error message is a log-style token, not a human-readable message

File: `YamlFrontmatterParser.kt`, line 101–103.

```kotlin
require(rawParsed.keys.all { it is String }) {
    "frontmatter_yaml_keys_must_be_strings"
}
```

All other `require` calls in this file produce human-readable diagnostic messages (e.g., `"Content does not start with YAML frontmatter delimiter (---)"`, `"Missing closing YAML frontmatter delimiter (---)"`, `"Frontmatter is not a YAML mapping"`). This one uses a log-style snake_case token. Additionally, it doesn't report which key failed or what its type was — the user who hits this error has no way to diagnose it from the exception message alone.

Align with the other require messages: provide a human-readable string that includes the offending key.

---

## Suggestions

### A. YAML sequences coerce to Java `ArrayList.toString()`, which is NOT re-parseable YAML

File: `YamlFrontmatterParser.kt`, line 108–109.

```kotlin
val yamlFields: Map<String, String> = (rawParsed as Map<String, Any>)
    .mapValues { (_, v) -> v.toString() }
```

A YAML sequence `tags: [wave1, backend]` becomes `"[wave1, backend]"` (Java's `ArrayList.toString()`), which is coincidentally similar to YAML syntax. A nested mapping `deps: {key: value}` becomes `"{key=value}"` (Java's `LinkedHashMap.toString()`), which is NOT valid YAML.

This is documented in the KDoc but is a lossy serialization that will surprise callers trying to work with list or map fields. If the role catalog loader is the next consumer (`YamlFrontmatterParser` is documented as reusable there), and role metadata has sequence fields, this will silently produce unparseable strings.

**Recommendation:** At minimum, add a test for nested map coercion to document the `{key=value}` output format explicitly, so this doesn't become a silent surprise for the role catalog loader when it arrives.

### B. Missing test for the `null` YAML scalar case

There is a test for unquoted ISO dates (`non-string-id.md`) confirming `StringOnlyResolver` works. There is no analogous test confirming behavior when a YAML value is the unquoted `null` keyword. Given the semantic issue in IMPORTANT #2 above, adding a test would at minimum pin the current behavior so a future change to `null` handling doesn't regress silently.

### C. `TicketParserImpl` is public but should arguably be internal

`TicketParserImpl` is a `class` with `public` visibility. Callers are expected to use `TicketParser.standard(outFactory)` via the companion factory. Making `TicketParserImpl` `internal` would enforce the intended access pattern. This is a packaging concern, not a functional bug.

---

## Documentation Updates Needed

None required. The KDoc on `YamlFrontmatterParser`, `FrontmatterParseResult`, and `TicketParser` is accurate and clear. The anchor point (`ap.mmcagXtg6ulznKYYNKlNP.E`) is correctly created and cross-linked.
