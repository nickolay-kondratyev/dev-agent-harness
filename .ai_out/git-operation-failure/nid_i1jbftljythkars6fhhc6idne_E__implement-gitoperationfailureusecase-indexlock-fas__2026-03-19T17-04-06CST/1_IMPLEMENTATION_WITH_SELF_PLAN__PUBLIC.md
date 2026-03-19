# GitOperationFailureUseCase Implementation

## Summary

Implemented `GitOperationFailureUseCase` (ref.ap.3W25hwJNB64sPy63Nc3OV.E) per spec at ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E.

V1 approach: **index.lock fast-path + fail-fast** for git operation failures.

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitIndexLockFileOperations.kt` | Interface + production impl for `.git/index.lock` file ops (exists/delete). Enables faking in tests. |
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCase.kt` | Interface + impl. Contains `GitFailureContext` data class. Factory companion pattern. |
| `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCaseImplTest.kt` | 16 unit tests with fakes for ProcessRunner, GitIndexLockFileOperations, FailedToExecutePlanUseCase. |

## Behavior

1. **index.lock fast-path**: When stderr contains `index.lock` or `unable to lock` AND `.git/index.lock` exists on disk:
   - Delete the lock file
   - Retry the original git command
   - If retry succeeds: return normally
   - If retry fails: fall through to fail-fast

2. **Fail-fast**: For all other failures, or when retry fails:
   - Log git command, stderr, and current `git status`
   - Escalate to `FailedToExecutePlanUseCase` with `PartResult.FailedWorkflow` containing full context

## Test Results

16 tests, 0 failures, 0 errors. All pass in `:app:test`.

## Design Decisions

- **`GitIndexLockFileOperations` as separate interface**: Clean testability seam. Production impl uses `java.nio.file.Files`. Tests use a simple fake.
- **`GitFailureContext` data class**: Captures part name, sub-part name, iteration number for error messages. Avoids passing many loose parameters.
- **ProcessRunner for retry**: Reuses the same ProcessRunner for retry and `git status` capture.
- **Case-insensitive matching**: `index.lock` and `unable to lock` patterns matched case-insensitively for robustness.
- **Best-effort git status**: If `git status` itself fails during fail-fast, a fallback message is used (not a crash).
