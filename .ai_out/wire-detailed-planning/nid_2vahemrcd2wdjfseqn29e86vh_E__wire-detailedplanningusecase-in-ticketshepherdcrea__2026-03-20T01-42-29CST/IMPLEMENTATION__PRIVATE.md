# Implementation Private State

## Completed

- Extracted `PartExecutorInfraBuilder` shared utility class
- Threaded `FailedToExecutePlanUseCase` through to planning factory
- Cleaned unused imports in `ProductionPlanningPartExecutorFactory`
- Added fail-fast test for null-planningParts stub
- All tests pass, detekt clean

## Design Decisions

- `PartExecutorInfraBuilder` uses a private constructor + companion methods (stateless utility class)
  since all methods are pure functions that take their deps as parameters
- Removed `shepherdContext` param from `buildGitCommitStrategy` since it was only needed for
  constructing `TmuxAllSessionsKiller` which is now part of the externalized `FailedToExecutePlanUseCase`
- `ProductionPartExecutorFactoryCreator` still constructs its own `FailedToExecutePlanUseCaseImpl`
  internally (pre-existing pattern) -- this is a separate concern from the planning factory fix

## Pre-Existing Issues (Not Addressed)

- `ProductionPartExecutorFactoryCreator` still creates its own `FailedToExecutePlanUseCaseImpl`
  internally rather than receiving it from the caller -- same pattern as before, just DRYed up
  the infrastructure methods
