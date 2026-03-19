# Implementation Review: GitOperationFailureUseCase

## Overall Assessment: **PASS**

The implementation is solid, follows the spec correctly, and adheres to project patterns. All tests pass (16 tests, 0 failures). The issues below are non-blocking but should be addressed for consistency with project standards.

---

## Summary

Implemented `GitOperationFailureUseCase` with index.lock fast-path recovery and fail-fast fallback per spec at ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E. Three files created: interface+impl for lock file ops, interface+impl for the use case, and a comprehensive test file with fakes.

All four spec scenarios are correctly implemented and tested:
- index.lock error + file exists + retry succeeds -> continue normally
- index.lock error + file exists + retry fails -> fail-fast
- index.lock error + file NOT exists -> fail-fast immediately
- Non-lock-related error -> fail-fast immediately

---

## BLOCKING Issues

None.

---

## IMPORTANT Issues

### 1. `Triple` usage in test helper violates CLAUDE.md

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCaseImplTest.kt:97-105`

CLAUDE.md explicitly states: "No `Pair`/`Triple` -- create descriptive `data class`."

The `createUseCase` helper returns `Triple<GitOperationFailureUseCase, FakeFailedToExecutePlanUseCase, FakeGitIndexLockFileOperations>`. This should be a descriptive data class:

```kotlin
private data class UseCaseTestFixture(
    val useCase: GitOperationFailureUseCase,
    val failedUseCase: FakeFailedToExecutePlanUseCase,
    val lockOps: FakeGitIndexLockFileOperations,
)
```

### 2. Two assertions in one `it` block

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCaseImplTest.kt:451-475`

The `it("THEN FailedWorkflow reason contains sub-part name and iteration")` block has two assertions:
```kotlin
reason shouldContain "stage-files"
reason shouldContain "3"
```

Per the one-assert-per-test standard, split into:
- `it("THEN FailedWorkflow reason contains sub-part name")`
- `it("THEN FailedWorkflow reason contains iteration number")`

---

## MINOR / Suggestions

None. The implementation is clean, well-structured, and follows existing patterns well.

---

## What Was Done Well

- **Clean testability seam**: `GitIndexLockFileOperations` interface enables easy faking without touching the filesystem in tests.
- **Follows existing patterns**: Factory companion, constructor injection, `outFactory.getOutForClass()` -- all consistent with `GitBranchManager`.
- **Comprehensive test coverage**: All four spec scenarios covered, plus error message content verification (git command, stderr, part name, sub-part, iteration, git status all checked).
- **Case-insensitive matching**: Robust against git version differences in error message casing.
- **Best-effort git status**: `getGitStatusBestEffort()` gracefully handles the case where `git status` itself fails during error reporting -- avoids a failure cascade.
- **`Nothing` return type on `failFast`**: Compiler enforces that the fail-fast path never returns normally.

---

## Documentation Updates Needed

None -- the spec already documents this use case, and the anchor point ref.ap.3W25hwJNB64sPy63Nc3OV.E is properly placed on the interface.
