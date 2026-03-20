# Implementation: Extract GitStagingCommitHelper

## What was done

Extracted the duplicated git staging/commit pattern from `FinalCommitUseCaseImpl` and `CommitPerSubPart` into a shared `GitStagingCommitHelper` class.

### Changes

1. **Created `GitStagingCommitHelper`** — encapsulates three methods:
   - `stageAll(failureContext)` — runs `git add -A` with error handling
   - `hasStagedChanges()` — runs `git diff --cached --quiet` to detect staged changes
   - `commit(vararg commitArgs, failureContext)` — runs caller-specified commit command with error handling

2. **Refactored `FinalCommitUseCaseImpl`** — removed private `stageAll()`, `hasStagedChanges()`, `commit()` methods; now delegates to an internal `GitStagingCommitHelper` instance.

3. **Refactored `CommitPerSubPart`** — same pattern; removed private methods and delegates to `GitStagingCommitHelper`.

4. **Added `GitStagingCommitHelperTest`** — 14 test cases covering:
   - `stageAll` success and failure paths (command, error output, failure context)
   - `hasStagedChanges` returning true/false
   - `commit` success and failure paths (with and without author)
   - Working directory (`-C <dir>`) support for all three methods

## Files modified

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitStagingCommitHelper.kt` | **NEW** — shared helper class |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt` | Refactored to delegate to `GitStagingCommitHelper` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt` | Refactored `CommitPerSubPart` to delegate to `GitStagingCommitHelper` |
| `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitStagingCommitHelperTest.kt` | **NEW** — unit tests for the helper |

## Design decisions

- `commit()` takes `vararg commitArgs` (args after "git") rather than structured parameters, keeping the helper generic. Callers control author, message format, etc.
- Both `FinalCommitUseCaseImpl` and `CommitPerSubPart` constructors still accept `processRunner`, `gitOperationFailureUseCase`, and `gitCommandBuilder` — they just wire them into the helper internally. This keeps the public API unchanged.
- No interface for `GitStagingCommitHelper` — it is a concrete implementation detail, not a seam for testing. Both consumers are tested via their existing fakes.

## Tests

All tests pass (BUILD SUCCESSFUL). No existing tests were modified.
