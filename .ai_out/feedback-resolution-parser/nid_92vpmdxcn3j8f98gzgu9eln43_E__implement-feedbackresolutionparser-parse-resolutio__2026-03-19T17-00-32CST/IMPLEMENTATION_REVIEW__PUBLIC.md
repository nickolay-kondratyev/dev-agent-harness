# Review: FeedbackResolutionParser Implementation

**Reviewer:** Code Review Agent
**Date:** 2026-03-19
**Branch:** `nid_92vpmdxcn3j8f98gzgu9eln43_E__implement-feedbackresolutionparser-parse-resolutio__2026-03-19T17-00-32CST`

---

## Summary

Implementation of `FeedbackResolutionParser` that parses `## Resolution:` markers from feedback
file content. The parser is correct, well-tested, and meets all specified requirements. Two
architectural issues worth addressing: package placement deviates from the established codebase
pattern, and the parser uses `class` where `object` would be more idiomatic and consistent.

**Overall assessment: GOOD -- two IMPORTANT items to address, no critical issues.**

**Verification:**
- All 13 test assertions pass (8 distinct scenarios).
- `sanity_check.sh` passes.
- Full `:app:test` suite passes.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Package placement inconsistent with codebase conventions

**Files:**
- `app/src/main/kotlin/com/glassthought/shepherd/feedback/FeedbackResolutionParser.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/feedback/FeedbackResolutionParserTest.kt`

**Problem:** The implementation lives at `com.glassthought.shepherd.feedback`, but the established
codebase pattern places stateless utility parsers under `core/supporting/{domain}/`. The existing
`YamlFrontmatterParser` lives at `com.glassthought.shepherd.core.supporting.ticket`. The
exploration document itself identified this and recommended `core/supporting/feedback/`.

The task spec said `Package: com.glassthought.shepherd.feedback`, but this conflicts with the
existing organizational convention. Consistency matters -- when a future developer looks for
parsers, they will check `core/supporting/` and not find this one.

**Suggestion:** Move to `com.glassthought.shepherd.core.supporting.feedback`:
- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/feedback/FeedbackResolutionParser.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/feedback/FeedbackResolutionParserTest.kt`

### 2. `class` should be `object` -- stateless parser has no reason to be instantiated

**File:** `app/src/main/kotlin/com/glassthought/shepherd/feedback/FeedbackResolutionParser.kt` (line 34)

**Problem:** `FeedbackResolutionParser` is a stateless utility with no constructor parameters. The
existing codebase convention uses `object` for stateless parsers (see `YamlFrontmatterParser` at
`app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/YamlFrontmatterParser.kt`
line 57). Using `class` means every call site must create an instance unnecessarily, and tests
allocate `val parser = FeedbackResolutionParser()` at the top.

The task spec explicitly said `class`, but `object` is the idiomatic Kotlin approach for this
pattern and matches the existing codebase. This is a case where following codebase conventions
should take precedence over a spec's interface sketch.

**Suggestion:** Change `class FeedbackResolutionParser` to `object FeedbackResolutionParser` and
update the test to call `FeedbackResolutionParser.parse(content)` directly without instantiation.

---

## Suggestions

### 1. Consider what happens with `## Resolution:    ` (whitespace-only after colon)

The current implementation correctly handles this -- `trim()` + `takeIf { it.isNotEmpty() }`
returns `MissingMarker`. This is tested in the "empty keyword" scenario. Good edge case coverage.

No action needed -- just noting this was verified.

### 2. The `MissingMarker` result conflates two distinct scenarios

`MissingMarker` is returned for both "no `## Resolution:` line at all" and "`## Resolution:` line
present but keyword is empty/blank." These are semantically different -- the first means the doer
forgot the section entirely, the second means the doer wrote the header but forgot to fill in the
keyword. The spec does not distinguish these, so this is acceptable for V1. If the caller later
needs to differentiate (e.g., for more specific error messages), a new `EmptyMarker` variant could
be added.

No action needed for now -- just a note for future consideration.

---

## Documentation Updates Needed

None -- this is a leaf utility class with no impact on existing CLAUDE.md or spec documentation.

---

## Test Coverage Assessment

All 7 required test cases are covered, plus a bonus edge case (empty keyword). The BDD structure
follows project conventions (GIVEN/WHEN/THEN with `AsgardDescribeSpec`). One assertion per `it`
block. Test content uses realistic feedback file examples. No issues found in tests.

---

## Correctness of Regex

The regex `^##\s+Resolution:\s*(.*)$` with `RegexOption.MULTILINE`:
- `^` anchors to start of line (correct with MULTILINE)
- `##\s+Resolution:` requires markdown H2 syntax with at least one space (correct)
- `\s*` allows optional whitespace after colon (correct)
- `(.*)` captures the keyword and any trailing content on the same line (correct -- `trim()` handles trailing whitespace)
- `$` anchors to end of line (correct with MULTILINE)

The header matching (`## Resolution:`) is case-sensitive while the keyword matching is
case-insensitive. This matches the spec requirement: "Case-insensitive keyword match after
`## Resolution:`". The header itself being case-sensitive is correct -- it is a fixed markdown
structure that the doer copies from instructions.
