# Plan Review -- Role Catalog Loader

## Executive Summary

The plan is well-structured, mirrors the TicketParser pattern correctly, and covers all ticket requirements. It is KISS and PARETO aligned. There are no blocking issues. I have two minor concerns worth addressing: (1) a potential test fragility with the `resourceDir` helper when loading from JAR resources, and (2) a missing test for error message content on the empty-directory case. Both are easily addressed inline. **Recommendation: APPROVED WITH MINOR REVISIONS -- revisions are small enough to apply inline. Plan iteration can be skipped.**

## Critical Issues (BLOCKERS)

None.

## Major Concerns

None.

## Minor Concerns

### 1. `resourceDir` helper -- `getResource` for directories in JARs

- **Concern:** Using `Class.getResource(...).toURI()` to resolve a *directory* path works fine when tests run against exploded class directories (the default Gradle behavior), but can fail if resources are ever loaded from a JAR (URI scheme becomes `jar:` and `Path.of()` throws). This is the same pattern TicketParser uses for individual files, so it is consistent, but worth noting that it only works because Gradle extracts test resources to a directory on the classpath.
- **Why:** This is not a blocker because Gradle test execution always uses exploded resources. But it is a known fragility if someone tries to repackage tests.
- **Suggestion:** No code change needed. Add a brief comment in the test helper: `// Works because Gradle runs tests against exploded resources, not JARs.`

### 2. Empty-directory error message assertion missing

- **Concern:** The test for the empty catalog directory only asserts `shouldThrow<IllegalArgumentException>` but does not verify the error message. The missing-description test correctly checks that "the error message contains the filename." The empty-directory case should similarly assert the message mentions the directory path or "no .md files" for debuggability.
- **Suggestion:** Add a `THEN error message indicates no .md files found` test case under the empty-catalog GIVEN block. This mirrors the pattern used for missing-description.

### 3. Non-existent directory error message assertion missing

- **Concern:** Same as above -- the non-existent directory test only checks `shouldThrow<IllegalArgumentException>` but does not verify the message content.
- **Suggestion:** Add a `THEN error message contains the directory path` test case.

## Simplification Opportunities (PARETO)

None needed. The plan is already at the right level of complexity for the scope.

## Inline Adjustments (Applied to Plan)

The following adjustments are minor enough that the implementor can incorporate them directly. No plan iteration is needed.

### Adjustment 1: Add error message assertions for empty-directory and non-existent directory

In Phase 4, under the test cases, add:

```
GIVEN an empty catalog directory (no .md files)
  WHEN load is called
    THEN throws IllegalArgumentException
    THEN error message indicates no .md files found

GIVEN a non-existent directory path
  WHEN load is called
    THEN throws IllegalArgumentException
    THEN error message contains the directory path
```

### Adjustment 2: Add comment to resourceDir helper

```kotlin
/**
 * Resolves a test resource subdirectory to a Path.
 * Works because Gradle runs tests against exploded resources, not JARs.
 */
fun resourceDir(name: String): Path = ...
```

## Strengths

- **Correct pattern mirroring:** The plan correctly mirrors the TicketParser pattern (interface + companion factory + Impl in single file, data class in separate file). This is consistent with the codebase and follows SRP.
- **Reuse of YamlFrontmatterParser:** No duplication. The existing utility is sufficient and the plan correctly identifies it as stateless and thread-safe.
- **Fail-fast behavior is well-specified:** All three failure modes (non-existent dir, empty dir, missing description) are covered with `IllegalArgumentException` and descriptive messages that include context (filename, directory path).
- **Resource management:** The plan explicitly calls out `.use {}` for `Files.walk()` stream -- this is critical and correctly handled.
- **`maxDepth = 1`:** Correct interpretation of "every .md file in `$CHAINSAW_AGENTS_DIR`" as flat directory (not recursive). The plan explicitly notes this design decision and documents why.
- **Test coverage is comprehensive:** Valid catalog, single role, empty catalog, non-existent directory, missing description, optional `description_long` present vs null, filename-to-name derivation -- all edge cases from the ticket are covered.
- **BDD structure with one-assert-per-test:** Follows the testing standards exactly as demonstrated by TicketParserTest.
- **No unnecessary dependencies:** Correctly identifies that snakeyaml is already present.
- **Clean phasing:** Data class first, then implementation, then test resources, then tests. Each phase has a clear verification step.
- **KISS:** No over-engineering. No abstraction layers beyond what is needed. No filtering flags, no recursive walking, no caching.

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**Rationale:** The plan is solid, well-aligned with codebase patterns, and covers all ticket requirements. The two minor adjustments (error message assertions in tests, comment on resourceDir) are small enough to apply during implementation without re-planning. **Plan iteration can be skipped -- proceed directly to implementation.**
