# Implementation: FinalCommitUseCase — Private Notes

## Status: COMPLETED

## Plan (executed)
1. Created `FinalCommitUseCaseImpl` following `CommitPerSubPart` pattern
2. Added `standard()` factory on `FinalCommitUseCase` companion
3. Changed `TicketShepherdCreatorImpl` to use `FinalCommitUseCaseFactory` (new fun interface)
4. Added `processRunnerFactory` param to `TicketShepherdCreatorImpl`
5. Wired `GitOperationFailureUseCase.standard()` inside `wireTicketShepherd()`
6. Updated `TicketShepherdCreatorTest` for renamed param
7. Wrote unit tests (13 test cases) and integration test (2 test cases, gated)
8. All tests pass

## Decisions
- Used factory pattern (`FinalCommitUseCaseFactory`) rather than direct `FinalCommitUseCase` param because the impl needs `ProcessRunner` and `GitOperationFailureUseCase` which are only available inside `wireTicketShepherd()`
- Added `processRunnerFactory` to `TicketShepherdCreatorImpl` following the same pattern as `ContextInitializerImpl`
- `GitOperationFailureUseCase` is now wired inside `wireTicketShepherd()` with `StandardGitIndexLockFileOperations` pointing at `repoRoot/.git`
- Used `GitFailureContext(partName="final-commit", subPartName="final-state", iterationNumber=0)` as static context since there's no sub-part iteration context for the final commit

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCase.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt` (NEW)
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImplTest.kt` (NEW)
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseIntegTest.kt` (NEW)
