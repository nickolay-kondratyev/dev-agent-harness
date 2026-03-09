# Implementation Report: TicketParser

**Ticket**: `nid_r9on08uqjmumuc6wi2c53e8p9_E` — Ticket Parser
**Branch**: `CC_nid_r9on08uqjmumuc6wi2c53e8p9_E__ticket-parser_sonnet-v4.6`

---

## Signal: COMPLETE

All plan items implemented. All tests green (iteration 2 review issues resolved).

---

## Files Created / Modified

### Modified
- `app/build.gradle.kts` — Added `implementation("org.yaml:snakeyaml:2.2")`

### Created — Main Sources
- `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketData.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParser.kt` — contains `FrontmatterParseResult` + `YamlFrontmatterParser`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketParser.kt` — contains `TicketParser` interface + `TicketParserImpl`

### Created — Test Sources
- `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParserTest.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/TicketParserTest.kt`

### Created — Test Resources
- `app/src/test/resources/com/glassthought/chainsaw/core/ticket/valid-ticket.md`
- `app/src/test/resources/com/glassthought/chainsaw/core/ticket/missing-id.md`
- `app/src/test/resources/com/glassthought/chainsaw/core/ticket/missing-title.md`
- `app/src/test/resources/com/glassthought/chainsaw/core/ticket/extra-fields-ticket.md`
- `app/src/test/resources/com/glassthought/chainsaw/core/ticket/empty-body-ticket.md`

### Modified — AP Cross-linking
- `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` — added `ap.mmcagXtg6ulznKYYNKlNP.E` below `## CLI Entry Point` heading

---

## Anchor Point
`ap.mmcagXtg6ulznKYYNKlNP.E`

Added to:
1. `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` under `## CLI Entry Point`
2. `TicketParser` interface KDoc as `ref.ap.mmcagXtg6ulznKYYNKlNP.E`

---

## Test Results

BUILD SUCCESSFUL. All non-integ tests pass.

---

## Decisions and Deviations from Plan

### Iteration 2 review fixes applied
1. **Split multi-assertion `it` blocks** — all three violations in `YamlFrontmatterParserTest.kt` split into single-assertion `it` blocks:
   - "THEN yamlFields contains all extra fields" → two `it` blocks (priority / assignee)
   - "THEN body contains all paragraphs" → three `it` blocks (first / second / third paragraph)
   - "THEN body contains text from both sides of the inner ---" → two `it` blocks (before / after)
2. **Non-string key guard** — added `require(rawParsed.keys.all { it is String })` in `YamlFrontmatterParser.parse()` before unchecked cast, with message "frontmatter_yaml_keys_must_be_strings".
3. **RESERVED_KEYS companion constant** — moved `reservedKeys` set out of `parse()` body into `companion object` as `RESERVED_KEYS` to avoid per-call allocation.

### Empty body test resource added
`empty-body-ticket.md` test resource + `TicketParserTest` case "THEN description is empty".

### Body-with-dashes edge case tests added
Covers body text on both sides of an inner `---`, and that `yamlFields` is correctly parsed from frontmatter only.

### snakeyaml non-thread-safety
`Yaml()` is instantiated per `parse()` call (not stored as a field on the `object`) to avoid snakeyaml's documented non-thread-safety. Per-call instantiation overhead is negligible.

### `additionalFields` type: `Map<String, Any>` not `Map<String, Any?>`
snakeyaml returns non-null `Any` values from `Yaml().load()` for a well-formed YAML map. The non-string key guard validates all keys are strings before the `@Suppress("UNCHECKED_CAST")` cast from `Map<*,*>` to `Map<String, Any>`.
