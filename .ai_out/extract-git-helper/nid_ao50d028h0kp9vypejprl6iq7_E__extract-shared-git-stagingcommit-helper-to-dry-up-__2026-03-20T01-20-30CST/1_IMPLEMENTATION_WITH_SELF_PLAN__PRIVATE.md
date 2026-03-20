# Private Notes: Extract GitStagingCommitHelper

## Status: COMPLETE

## What was done
- Created `GitStagingCommitHelper` with `stageAll`, `hasStagedChanges`, `commit` methods
- Refactored `FinalCommitUseCaseImpl` to delegate (removed 3 private methods)
- Refactored `CommitPerSubPart` to delegate (removed 3 private methods)
- Added 14 unit tests for the helper
- All tests pass (BUILD SUCCESSFUL)

## Key files
- Helper: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitStagingCommitHelper.kt`
- Test: `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitStagingCommitHelperTest.kt`
- Modified: `FinalCommitUseCaseImpl.kt`, `GitCommitStrategy.kt` (contains `CommitPerSubPart`)

## Notes
- `commit()` uses `vararg commitArgs` with a named `failureContext` parameter
- No interface needed for the helper — it is an internal implementation detail
- Constructor signatures of both consumers unchanged (factory methods still work)
- Test fakes (`RecordingFakeProcessRunner`, `FakeGitOperationFailureUseCase`) reused from `CommitPerSubPartTest`
