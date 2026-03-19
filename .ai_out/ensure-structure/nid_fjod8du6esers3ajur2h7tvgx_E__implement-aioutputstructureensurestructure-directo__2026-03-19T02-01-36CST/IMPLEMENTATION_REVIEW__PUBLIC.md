# Implementation Review: AiOutputStructure.ensureStructure()

## Verdict: PASS -- READY TO SHIP (with one NON-BLOCKING issue)

## Summary

Added `ensureStructure(parts: List<Part>)` to `AiOutputStructure` that creates the full `.ai_out/` directory skeleton. The implementation is clean, correct, idempotent, and matches the spec. Test coverage is thorough with 34 test cases across 7 scenarios. No existing tests or functionality were removed.

## Verification

- `sanity_check.sh` -- PASSED
- `AiOutputStructureEnsureStructureTest` -- ALL 34 TESTS PASSED
- Existing `AiOutputStructureTest` -- UNMODIFIED, still passing
- No files removed, no existing behavior changed

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

None.

---

## Suggestions

### 1. NON-BLOCKING: Resource leak in `Files.walk` (test code)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructureEnsureStructureTest.kt`, line 205

```kotlin
val hasFeedback = Files.walk(branchRoot).anyMatch { it.fileName?.toString() == "__feedback" }
```

`Files.walk()` returns a `Stream` that wraps a `DirectoryStream` -- a closeable resource backed by an OS file handle. Per the Javadoc: "If timely disposal of file system resources is required, the try-with-resources construct should be used." Not closing it can leak file descriptors, especially under repeated test runs.

**Fix**:
```kotlin
val hasFeedback = Files.walk(branchRoot).use { stream ->
    stream.anyMatch { it.fileName?.toString() == "__feedback" }
}
```

This is non-blocking because the test uses a temp directory that gets cleaned up via `afterSpec`, and the stream is short-lived. But it violates the CLAUDE.md standard on resource management ("use `.use{}` pattern ... No resource leaks").

### 2. NON-BLOCKING: Consider testing empty parts list

The ticket's review criteria mention "Empty parts list?" as an edge case. The current implementation handles it correctly (only creates `harness_private/` and `shared/plan/`), but there is no explicit test for it. A one-liner test would document this behavior:

```kotlin
describe("GIVEN ensureStructure with empty parts list") {
    val tempDir = Files.createTempDirectory("ai-out-empty-parts-test")
    val structure = AiOutputStructure(repoRoot = tempDir, branch = "empty_branch")

    structure.ensureStructure(emptyList())

    it("THEN harness_private exists") {
        Files.isDirectory(structure.harnessPrivateDir()) shouldBe true
    }

    it("THEN shared/plan exists") {
        Files.isDirectory(structure.sharedPlanDir()) shouldBe true
    }

    it("THEN no planning or execution directories exist") {
        val branchRoot = structure.branchRoot()
        Files.exists(branchRoot.resolve("planning")) shouldBe false
        Files.exists(branchRoot.resolve("execution")) shouldBe false
    }

    afterSpec {
        tempDir.toFile().deleteRecursively()
    }
}
```

---

## Correctness Analysis

| Requirement | Implemented | Tested |
|---|---|---|
| Creates `harness_private/` always | YES (line 122) | YES (lines 50-52, 145-146, 178-179, 227-228) |
| Creates `shared/plan/` always | YES (line 123) | YES (lines 54-56, 182-183, 230-231) |
| Planning: `planning/${subPart}/private/` | YES (line 129) | YES (lines 60-62) |
| Planning: `planning/${subPart}/comm/{in,out}/` | YES (lines 130-131) | YES (lines 64-70) |
| Execution: `__feedback/{pending,addressed,rejected}/` | YES (lines 136-138) | YES (lines 74-85) |
| Execution: `${subPart}/private/` and `comm/{in,out}/` | YES (lines 141-143) | YES (lines 88-110) |
| Idempotent (safe to call twice) | YES (uses `Files.createDirectories`) | YES (lines 132-163) |
| No files created | YES (only `Files.createDirectories` calls) | Verified by inspection |
| No `__feedback` under planning | N/A (correct by construction) | YES (lines 113-124, 203-207) |
| Planning-only: no execution dirs | Correct by construction | YES (lines 198-201) |
| Execution-only: no planning dirs | Correct by construction | YES (lines 235-238) |

## Architecture Assessment

- The `when` on `Phase` is exhaustive (no `else` branch) -- compiler will enforce if a new phase is added. Follows CLAUDE.md sealed/enum standard.
- `ensureStructure()` reuses existing path-resolution methods rather than duplicating path logic. Good DRY.
- The class KDoc was updated to reflect that `ensureStructure()` adds I/O to a previously pure class. The method KDoc is thorough.
- No changes to existing tests or companion object constants.

## Documentation Updates Needed

None. The spec (`doc/schema/ai-out-directory.md`) already references `ensureStructure()` at line 196.
