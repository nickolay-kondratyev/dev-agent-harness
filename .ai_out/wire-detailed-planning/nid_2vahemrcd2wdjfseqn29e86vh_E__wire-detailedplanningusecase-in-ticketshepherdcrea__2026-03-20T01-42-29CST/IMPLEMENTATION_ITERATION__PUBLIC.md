# Implementation Iteration: Fix Review Feedback for Wire DetailedPlanningUseCaseImpl

## Summary

Addressed all IMPORTANT issues and applicable suggestions from the implementation review.

## Changes Made

### Issue 1: DRY Violation -- Extracted PartExecutorInfraBuilder

Created `PartExecutorInfraBuilder` in the `executor` package as a shared utility class with companion methods:
- `loadRoleDefinitions()` -- loads role definitions from env-var-specified directory
- `buildAgentFacade()` -- constructs full AgentFacadeImpl with all sub-dependencies
- `buildGitCommitStrategy()` -- constructs GitCommitStrategy with injected FailedToExecutePlanUseCase

Both `ProductionPartExecutorFactoryCreator` and `ProductionPlanningPartExecutorFactory` now delegate
to `PartExecutorInfraBuilder` instead of duplicating ~150 lines of infrastructure construction.

### Issue 2: Duplicate FailedToExecutePlanUseCaseImpl

`ProductionPlanningPartExecutorFactory.create()` now accepts a `failedToExecutePlanUseCase` parameter
instead of constructing its own internally. The `wireDetailedPlanningUseCase()` method in
`TicketShepherdCreatorImpl` passes `ctx.failedToExecutePlanUseCase` (the one already constructed
in `wireTicketShepherd`), ensuring tests use test fakes rather than real `DefaultProcessExiter`.

The `shepherdContext` parameter was also removed from `buildGitCommitStrategy()` since it was only
needed to construct the now-externalized `FailedToExecutePlanUseCaseImpl`.

### Suggestion: Unused Imports

Cleaned up all unused imports in `ProductionPlanningPartExecutorFactory.kt` (`Val`, `ValType`,
`AgentFacade`, and several others that were artifacts of the pre-extraction code).

### Suggestion: Fail-Fast Test

Added test in `TicketShepherdCreatorTest` verifying that `wireDetailedPlanningUseCase` returns a
stub that throws `IllegalStateException` with "bug" in the message when invoked for a
straightforward workflow (null planningParts).

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorInfraBuilder.kt` | NEW -- shared infra builder |
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/ProductionPartExecutorFactoryCreator.kt` | Delegates to PartExecutorInfraBuilder, removes ~100 lines |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactory.kt` | Delegates to PartExecutorInfraBuilder, accepts failedToExecutePlanUseCase param, cleans imports |
| `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` | Passes failedToExecutePlanUseCase to ProductionPlanningPartExecutorFactory.create() |
| `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt` | Adds fail-fast stub test |

## Tests

All tests pass (BUILD SUCCESSFUL). Detekt clean.
