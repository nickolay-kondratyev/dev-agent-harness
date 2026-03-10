# Plan Review: Workflow JSON Parser

## Executive Summary

The plan is well-structured, follows existing codebase patterns closely (TicketParser), and correctly maps the design doc specification. There are a few concerns that should be addressed: one inconsistency with the existing TicketParser pattern (explicit file-existence check vs. letting IO exceptions propagate), one missing test case (both parts AND planning fields present), and a minor consideration about `@JsonProperty` for camelCase field names. None of these are blockers -- all are minor and can be addressed inline.

## Critical Issues (BLOCKERS)

None.

## Major Concerns

None.

## Minor Concerns

### 1. File-existence check diverges from TicketParser pattern (MINOR)

**Description:** The plan proposes an explicit `path.exists()` check before reading, throwing `IllegalArgumentException("Workflow file not found: $path")`. However, the existing `TicketParser` does NOT do this -- it simply calls `path.readText()` and lets `NoSuchFileException` (or `IOException`) propagate naturally.

**Why it matters:** Consistency with existing patterns. The explicit check also introduces a TOCTOU race condition (file could be deleted between exists-check and read), though this is negligible for config files parsed at startup.

**Recommendation:** Either approach is acceptable. Since the plan explicitly chooses to diverge for a better error message, that is a defensible decision. However, consider wrapping the `readText()` call in a try-catch for `NoSuchFileException` instead of a separate `exists()` check -- this avoids the TOCTOU issue while still providing a clear error message. Alternatively, just let the `NoSuchFileException` propagate; it already contains the path and is quite descriptive.

**Verdict:** MINOR -- the implementor can choose. Not blocking.

### 2. Missing test: both `parts` AND `planningPhases` present (MINOR)

**Description:** The plan has a test for "neither parts nor planning" but does NOT have a test for "BOTH parts AND planningPhases present" (which should also fail validation per the mutual exclusivity rule described in Phase 3, step 5.2).

**Recommendation:** Add a test fixture `both-parts-and-planning.json` and a test case:

```
GIVEN a JSON file with both parts and planningPhases
  WHEN parse is called
    THEN throws IllegalArgumentException
```

This is explicitly described in the validation logic ("Not both, not neither") but only the "not neither" case is tested.

### 3. `@JsonProperty` for camelCase JSON fields (MINOR)

**Description:** The plan correctly notes that Jackson Kotlin module handles data classes natively and `@JsonProperty` is not needed because JSON field names match Kotlin property names. This is correct. However, Jackson's default property naming strategy is based on Java Bean conventions, and camelCase like `planningPhases` maps directly. Just want to confirm this is indeed a non-issue with `jackson-module-kotlin` -- and it is. The Kotlin module uses constructor parameter names, not getter-based discovery. No action needed.

### 4. Missing `with-planning` validation completeness (MINOR)

**Description:** The plan says validation should check that with-planning workflows have `planningPhases` + `planningIteration` + `executionPhasesFrom` all present. But the data model makes `planningIteration` and `executionPhasesFrom` nullable. Consider: what if someone provides `planningPhases` but forgets `planningIteration`? The plan's validation logic (Phase 3, step 5.2) says to check for all three being present, which is correct. Just ensure the implementation actually validates all three, not just `planningPhases`.

**Recommendation:** The plan already describes this correctly. Just a note to the implementor: do not shortcut to checking only `planningPhases != null`.

### 5. `IterationConfig` could just be an `Int` (SIMPLIFICATION CONSIDERATION)

**Description:** `IterationConfig` is a data class wrapping a single `Int` field (`max`). This adds a level of nesting. However, the JSON schema in the design doc explicitly uses `{ "max": 4 }`, so this wrapping is required to match the JSON structure faithfully. No change needed -- the plan is correct to follow the design doc.

## Simplification Opportunities (PARETO)

### Consider: `org.json` vs. Jackson coexistence

The codebase already uses `org.json` (for LLM API request/response bodies in `GLMHighestTierApi`). Adding Jackson creates two JSON libraries. This is fine for now -- `org.json` is used for ad-hoc JSON construction (imperative), while Jackson is used for structured deserialization (declarative). They serve different purposes. However, long-term, consider migrating `org.json` usage to Jackson for consistency. This is NOT in scope for this ticket -- just a note.

**No action needed.**

## Test Strategy Assessment

The test coverage is solid:
- Happy path for both workflow types (straightforward, with-planning)
- Multi-part list ordering
- File not found
- Malformed JSON
- Missing required fields
- Empty phases validation
- Neither-parts-nor-planning validation

**One gap:** Add "both parts AND planning present" test (see Minor Concern #2 above).

The BDD structure follows the existing `TicketParserTest` pattern exactly. The `resourcePath` helper is consistent. One assert per `it` block. Good.

## Strengths

1. **Pattern consistency:** The plan faithfully mirrors the `TicketParser` pattern (interface + companion factory, `OutFactory` injection, `suspend fun`, `Dispatchers.IO`). This is exactly right.

2. **Scope discipline:** The plan explicitly calls out what is NOT in scope (role name validation, `executionPhasesFrom` resolution, CLI integration). Clean separation of concerns.

3. **Fail-fast philosophy:** Strict unknown-property handling, required-field validation, and post-deserialization structural validation. This matches the codebase's existing approach.

4. **Design doc fidelity:** The JSON schemas, field names, and domain model match the design doc precisely. No drift.

5. **Comprehensive test fixtures:** Seven test fixture files covering happy paths and error cases. The `multi-part.json` fixture tests an important edge case (list ordering).

6. **ObjectMapper configuration:** `FAIL_ON_NULL_FOR_PRIMITIVES = true` for `IterationConfig.max` is a smart defensive choice.

7. **Anchor points properly assigned:** Both the interface and domain model get anchor points, with cross-references to the design doc AP.

8. **Jackson version choice:** Using direct dependency declaration (not version catalog) -- consistent with how `okhttp`, `snakeyaml`, and `org.json` are declared in `build.gradle.kts`.

## Inline Adjustments Made (MINOR)

None needed in the plan document itself. The concerns above are all implementation-time considerations that the implementor can address.

## Verdict

- [x] **APPROVED WITH MINOR REVISIONS**
- [ ] APPROVED
- [ ] NEEDS REVISION
- [ ] REJECTED

### Required Minor Revisions (non-blocking, implementor can address during implementation):

1. Add a test case for "both parts AND planning present" -- mutual exclusivity validation needs coverage from both directions.
2. Consider whether the explicit `path.exists()` check should be replaced with catching `NoSuchFileException` from `readText()` for consistency with `TicketParser` pattern and to avoid TOCTOU.

### PLAN_ITERATION: Can be SKIPPED

The concerns are all MINOR and can be addressed by the implementor during implementation without requiring a plan revision cycle. The plan's architecture, domain model, test strategy, and phasing are all sound.
