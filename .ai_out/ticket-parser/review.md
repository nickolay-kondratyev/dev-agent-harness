# TicketParser Implementation Review

**Branch:** `CC_nid_r9on08uqjmumuc6wi2c53e8p9_E__ticket-parser_sonnet-v4.6`
**Divergence commit:** `b0560267f5ceb7bd3d3941d9ba1cc8442f311595`
**Verdict: NOT READY**

---

## Summary

The `TicketParser` component (`YamlFrontmatterParser`, `TicketData`, `TicketParserImpl`) is well-structured overall. The interface/factory pattern is correct, IO dispatch is correct, and snakeyaml thread-safety is handled correctly (new instance per call). Test coverage structure is solid.

However, two substantive bugs were found — both rooted in how snakeyaml's type resolution leaks through the abstraction boundary.

---

## IMPORTANT Issues

### 1. Silent type corruption for `id` and `title` when YAML value is not a string

**File:** `TicketParser.kt`, lines ~56–60

```kotlin
val id = result.yamlFields["id"]?.toString()
    ?: throw IllegalArgumentException("Ticket is missing required field: id")
```

`?.toString()` coerces **any** snakeyaml-resolved type silently. The dangerous case: snakeyaml resolves unquoted ISO date strings as `java.util.Date`. A ticket with an unquoted `id: 2026-03-09T23:05:48Z` in its frontmatter would be parsed to a `java.util.Date` and then `.toString()` would store something like `"Mon Mar 09 18:05:48 CDT 2026"` as the ticket ID — no exception raised, silent corrupt data.

**Fix:** Require `String` instance explicitly:
```kotlin
val id = (result.yamlFields["id"] as? String)
    ?: throw IllegalArgumentException("Ticket field 'id' must be a string value")
```

This also correctly rejects `id: [a, b]` (List) and `id: 123` (Integer) rather than coercing them.

---

### 2. `Map<String, Any>` leaks snakeyaml raw types into `additionalFields` — a ClassCastException waiting to happen

**Files:** `YamlFrontmatterParser.kt` (line 12), `TicketData.kt` (line 18)

The `Any` in `Map<String, Any>` is not general `Any` — it is one of snakeyaml's resolved types (`String`, `Integer`, `Double`, `Boolean`, `ArrayList`, `LinkedHashMap`, or `java.util.Date`). This is already happening in the test fixture: `extra-fields-ticket.md` contains:

```yaml
created_iso: 2026-03-09T23:05:48Z
```

This is stored in `additionalFields["created_iso"]` as `java.util.Date`. Any future caller doing `additionalFields["created_iso"] as String` gets a `ClassCastException` at runtime. The existing tests do not catch this because they only check `shouldContainKey`, never the value type.

The role catalog loader is also planned to reuse `YamlFrontmatterParser` — this bug will propagate there too.

**Option A (minimal fix):** Add explicit documentation + a test that asserts type of date-formatted values in `additionalFields` is `java.util.Date`, making the contract explicit and non-surprising.

**Option B (correct long-term fix):** Configure snakeyaml to treat all values as strings — e.g. via `SafeConstructor` with string-only resolution, or by post-processing the map. This eliminates the leakage entirely. Given that `YamlFrontmatterParser` is being reused across multiple markdown file types, `Map<String, String>` is a far more defensible contract.

---

## Minor (not blocking, but worth noting)

### 3. `trimStart('\n')` strips ALL leading newlines, comment says "a blank line"

**File:** `YamlFrontmatterParser.kt`, line 74

```kotlin
val body = bodyLines.joinToString("\n").trimStart('\n')
```

The comment says "strip any leading blank line" (singular). `trimStart('\n')` strips all of them. This is probably harmless for markdown, but the code and comment are out of sync. Use `removePrefix("\n")` to match documented intent, or update the comment.

### 4. Inconsistent error message style

**File:** `YamlFrontmatterParser.kt`, line 67

```kotlin
require(rawParsed.keys.all { it is String }) {
    "frontmatter_yaml_keys_must_be_strings"
}
```

Every other `require` in this file uses a human-readable sentence. This one uses a snake_case log token. If it surfaces in error output it will read as code, not an explanation. Make it consistent.

---

## Action Required

1. Fix `id`/`title` extraction to use `as? String` with explicit failure (Issue #1).
2. Resolve the `Map<String, Any>` type leakage — either document + test the concrete types callers will actually receive, or switch to all-strings resolution in snakeyaml (Issue #2). Recommend Option B given planned reuse across multiple markdown file types.
