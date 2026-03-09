# Implementation Review: TicketParser

**Reviewer**: IMPLEMENTATION_REVIEWER
**Branch**: `CC_nid_r9on08uqjmumuc6wi2c53e8p9_E__ticket-parser_sonnet-v4.6`
**Signal**: **NEEDS_ITERATION**

---

## Summary

The implementation is well-structured overall. The core architecture is sound: `YamlFrontmatterParser` as a reusable `object`, `TicketParser` interface + `TicketParserImpl`, proper `suspend fun` with `withContext(Dispatchers.IO)` for file I/O, fail-fast on missing required fields, `additionalFields` for extensibility, and structured logging via `Out`/`Val`/`ValType`. The anchor point requirement is met. Tests pass.

However, there are **two IMPORTANT violations of CLAUDE.md testing standards** that must be fixed before approval.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Multiple Assertions in Single `it` Blocks — Violates One-Assert-Per-Test Standard

`CLAUDE.md` testing standards are explicit: **each `it` block must contain one logical assertion**. Three `it` blocks violate this.

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParserTest.kt`

**Violation 1** — lines 96-100: two `shouldContainKey` calls in one `it` block:
```kotlin
it("THEN yamlFields contains all extra fields") {
    val result = YamlFrontmatterParser.parse(content)
    result.yamlFields shouldContainKey "priority"   // assertion 1
    result.yamlFields shouldContainKey "assignee"   // assertion 2
}
```

Fix — split into two `it` blocks:
```kotlin
it("THEN yamlFields contains 'priority'") {
    YamlFrontmatterParser.parse(content).yamlFields shouldContainKey "priority"
}
it("THEN yamlFields contains 'assignee'") {
    YamlFrontmatterParser.parse(content).yamlFields shouldContainKey "assignee"
}
```

**Violation 2** — lines 119-125: three `shouldContain` calls in one `it` block:
```kotlin
it("THEN body contains all paragraphs") {
    val result = YamlFrontmatterParser.parse(content)
    result.body shouldContain "First paragraph here."   // assertion 1
    result.body shouldContain "Second paragraph here."  // assertion 2
    result.body shouldContain "Third paragraph here."   // assertion 3
}
```

Fix — split into three `it` blocks:
```kotlin
it("THEN body contains first paragraph") { ... }
it("THEN body contains second paragraph") { ... }
it("THEN body contains third paragraph") { ... }
```

**Violation 3** — lines 143-147: two `shouldContain` calls in one `it` block:
```kotlin
it("THEN body contains text from both sides of the inner ---") {
    val result = YamlFrontmatterParser.parse(content)
    result.body shouldContain "Body text before dashes."  // assertion 1
    result.body shouldContain "Body text after dashes."   // assertion 2
}
```

Fix — split into two `it` blocks.

### 2. `@Suppress("UNCHECKED_CAST")` Cast Is Unsound for Non-String YAML Keys

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParser.kt`, line 67

```kotlin
@Suppress("UNCHECKED_CAST")
val yamlFields = rawParsed as Map<String, Any>
```

SnakeYAML parses YAML key types based on YAML spec — a YAML map with integer keys (e.g., `1: value`) or boolean keys will produce a `Map<Any, Any>` at runtime. The JVM cast to `Map<String, Any>` succeeds at cast-site (type erasure), but downstream access of `yamlFields["someKey"]` would silently miss entries with non-string keys, or worse, a `ClassCastException` could surface in callers.

For ticket files this is unlikely, but `YamlFrontmatterParser` is documented as reusable for role catalog files too. The fix is to explicitly filter and validate keys:

```kotlin
require(rawParsed.keys.all { it is String }) {
    "Frontmatter YAML keys must all be strings"
}
@Suppress("UNCHECKED_CAST")
val yamlFields = rawParsed as Map<String, Any>
```

This makes the assumption explicit and fail-fast rather than silently broken.

---

## Suggestions

### S1. `trimStart('\n')` on body discards leading blank lines intentionally but asymmetrically

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParser.kt`, line 70

```kotlin
val body = bodyLines.joinToString("\n").trimStart('\n')
```

`trimStart('\n')` strips ALL leading newlines (not just one). If a ticket intentionally started its body with a blank line, this information is silently lost. The current behavior is fine for the documented use case (one blank line between `---` and body), but the comment on line 69 says "strip any leading blank line that follows the closing ---" (singular), while `trimStart` strips any number.

This is acceptable as-is for the current use case but worth noting. Consider `removePrefix("\n")` for precision if the intent is to strip exactly one leading newline. Not blocking, but behavior should match the comment.

### S2. `YamlFrontmatterParser` `parse()` not declared `suspend` — calling context must be aware

The YAML parsing is CPU-bound (in-memory string processing). Currently `TicketParserImpl` calls `YamlFrontmatterParser.parse()` outside of `withContext(Dispatchers.IO)`. Since this is pure in-memory work on an already-loaded string, this is fine. Just confirm this stays that way if `YamlFrontmatterParser` is ever extended to do file I/O.

### S3. `reservedKeys` set created on every `parse()` call

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketParser.kt`, line 60

```kotlin
val reservedKeys = setOf("id", "title", "status")
```

Minor allocation, but could be a companion object constant. Not blocking.

---

## Checklist

| Requirement | Status |
|---|---|
| `TicketData` data class with `id`, `title`, `status`, `description`, `additionalFields` | PASS |
| `TicketParser` interface + `TicketParserImpl` | PASS |
| `YamlFrontmatterParser` reusable utility | PASS |
| snakeyaml dependency added to `build.gradle.kts` | PASS |
| Package `com.glassthought.chainsaw.core.ticket` | PASS |
| Fail-fast on missing `id` with `IllegalArgumentException` | PASS |
| Fail-fast on missing `title` with `IllegalArgumentException` | PASS |
| `parse()` is `suspend` with `withContext(Dispatchers.IO)` for file I/O | PASS |
| Extra frontmatter fields in `additionalFields` | PASS |
| Reserved fields NOT in `additionalFields` | PASS |
| Anchor point `ref.ap.mmcagXtg6ulznKYYNKlNP.E` in `TicketParser` KDoc | PASS |
| `ap.mmcagXtg6ulznKYYNKlNP.E` added below `## CLI Entry Point` in design ticket | PASS |
| Logging uses `Out`/`Val`/`ValType` — no embedded strings | PASS |
| No `println` in production code | PASS |
| One assert per `it` block | FAIL — 3 violations |
| BDD GIVEN/WHEN/THEN structure | PASS |
| Body-with-dashes edge case tested | PASS |
| Empty body tested | PASS |
| Tests pass (non-integ) | PASS |

---

## Required Changes Before Approval

1. **Split multi-assertion `it` blocks in `YamlFrontmatterParserTest.kt`** — violations at lines 96-100, 119-125, and 143-147. Each `it` block must contain exactly one assertion.
2. **Add non-string key guard in `YamlFrontmatterParser.parse()`** — validate that all YAML keys are strings before the unchecked cast, throwing `IllegalArgumentException` with a clear message if not.
