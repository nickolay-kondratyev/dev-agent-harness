# Implementation Review: AiOutputStructure

## Summary

The implementation delivers a clean, focused `AiOutputStructure` class that manages path resolution and directory creation for the `.ai_out/${branch}/` directory tree. All ticket requirements are met, the plan reviewer feedback (planning role methods + `ensureStructure` planning support) was incorporated, and all 32 tests pass. The code follows project conventions well -- constructor injection, named constants, BDD tests with one assert per test, and proper anchor point cross-referencing.

**Overall assessment: APPROVED WITH MINOR REVISIONS**

The implementation is solid. Two important issues should be addressed before merging to prevent silent bugs.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Empty/blank string parameters silently produce incorrect paths

**File:** `app/src/main/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructure.kt`

**Problem:** `Path.resolve("")` collapses the empty segment entirely. For example:
- `branchRoot("")` returns `repoRoot/.ai_out` instead of `repoRoot/.ai_out/<branch>/`
- `phaseRoleDir("branch", "", "IMPLEMENTOR")` returns `.ai_out/branch/phases/IMPLEMENTOR` -- missing the part segment

This violates the "fail-fast" principle the class already applies to `repoRoot`.

**Recommendation:** Add `require` checks for `branch`, `role`, and `part` parameters. Since these are used in multiple methods, a private helper keeps it DRY:

```kotlin
private fun requireNotBlank(value: String, paramName: String) {
    require(value.isNotBlank()) { "$paramName must not be blank" }
}
```

Then call it in each public method or, more practically, in the three private/internal building-block methods (`branchRoot`, `phaseRoleDir`, `planningRoleDir`).

### 2. Temp directory leak in tests -- `Pair` usage

**File:** `app/src/test/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructureTest.kt` (line 17-19)

**Problem:** Two issues with `createStructureWithTempDir()`:

(a) It returns `Pair<Path, AiOutputStructure>`, which contradicts the CLAUDE.md rule: "No `Pair`/`Triple` -- create descriptive `data class`." A simple `data class TestFixture(val repoRoot: Path, val structure: AiOutputStructure)` would be more explicit and was already suggested in the plan.

(b) The created temp directories are never cleaned up. While OS temp folders are eventually cleaned, this creates 6 uncleaned temp directories per test run. Consider using Kotest's `tempdir()` (from `AsgardDescribeSpec`) if available, or adding cleanup in an `afterSpec` block. If the project convention is to not worry about OS temp cleanup in tests, this is acceptable.

**Recommendation:** At minimum, replace `Pair` with `data class TestFixture`.

---

## Suggestions

### 1. Consider validating that `repoRoot` is a directory, not just that it exists

The constructor checks `Files.exists(repoRoot)` but not `Files.isDirectory(repoRoot)`. If someone passes a file path, the constructor succeeds but `ensureStructure` would fail later with a confusing error when `Files.createDirectories` tries to create a subdirectory under a file. A more precise check:

```kotlin
require(Files.isDirectory(repoRoot)) {
    "Repository root is not a directory: [${repoRoot}]"
}
```

### 2. Test structure assertion: consider asserting full resolved path, not just suffix

Most path resolution tests use `shouldEndWith` which verifies the suffix but not the full structure. For example, `result.toString() shouldEndWith "phases/$part/$role"` would pass even if the path were `repoRoot/wrong/.ai_out/phases/part_1/IMPLEMENTOR`. The `harnessPrivateDir` tests already check both prefix (starts with repoRoot) and suffix -- consider applying that pattern to at least one test per method group to catch potential path construction errors.

This is a minor suggestion -- the current approach is pragmatic and the risk is low since the resolution methods are trivially correct.

---

## Requirements Checklist

| Requirement | Status |
|---|---|
| `harnessPrivateDir(branch)` | DONE |
| `sharedDir(branch)` | DONE |
| `planDir(branch)` | DONE |
| `planningRoleDir(branch, role)` | DONE |
| `phaseRoleDir(branch, part, role)` | DONE |
| `sessionIdsDir(branch, part, role)` | DONE |
| `publicMd(branch, part, role)` | DONE |
| `privateMd(branch, part, role)` | DONE |
| `sharedContextMd(branch)` | DONE |
| `locationsFile(branch)` | DONE |
| `ensureStructure(branch, parts)` | DONE (with planningRoles) |
| Planning role methods (review feedback) | DONE |
| Fail-fast constructor | DONE |
| Idempotent directory creation | DONE |
| No magic strings | DONE |
| Constructor injection | DONE |
| BDD tests with one assert per test | DONE |
| Anchor point created | DONE (`ap.XBNUQHLjDLpAr8F9IOyXU.E`) |
| `ref.ap` in KDoc | DONE |
| No `build.gradle.kts` changes | DONE |
| All tests pass | DONE (32/32) |

---

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**Rationale:** Clean implementation that meets all requirements. The two IMPORTANT issues (blank string validation and `Pair` usage in tests) are low-risk but should be fixed for consistency with project standards. Neither is a blocker.
