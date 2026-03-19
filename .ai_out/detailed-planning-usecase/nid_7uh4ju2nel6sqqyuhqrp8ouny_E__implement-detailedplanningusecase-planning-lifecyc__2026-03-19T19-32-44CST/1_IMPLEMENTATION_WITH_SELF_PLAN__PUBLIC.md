# Implementation: DetailedPlanningUseCase

## What Was Done

Implemented the `DetailedPlanningUseCase` — the planning lifecycle use case that creates a planning executor, runs it, converts approved plans to execution parts, and handles failures.

### Files Created

1. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCase.kt`**
   - `DetailedPlanningUseCase` fun interface with `suspend fun execute(): List<Part>`
   - `DetailedPlanningUseCaseImpl` with full planning lifecycle:
     - Creates planning executor via `PlanningPartExecutorFactory`
     - Runs executor, handles all `PartResult` failure types by delegating to `FailedToExecutePlanUseCase`
     - On success, calls `PlanFlowConverter.convertAndAppend()` to convert plan to execution parts
     - On `PlanConversionException`, retries the full planning loop with a configurable retry budget
     - When retry budget exhausted, delegates to `FailedToExecutePlanUseCase` with `FailedToConverge`

2. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/PlanningPartExecutorFactory.kt`**
   - `PlanningPartExecutorFactory` fun interface that creates a `PartExecutor` for the planning phase
   - Keeps `DetailedPlanningUseCaseImpl` testable

3. **`app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCaseImplTest.kt`**
   - 12 test cases covering:
     - Happy path (returns execution parts, correct size, no failure delegation)
     - `FailedWorkflow` delegation (handleFailure called, correct PartResult, converter not called)
     - `AgentCrashed` delegation
     - `FailedToConverge` delegation
     - `PlanConversionException` retry success (parts returned, factory creates 2 executors, no failure call)
     - `PlanConversionException` retry budget exhaustion (failure delegation, FailedToConverge result, correct executor count)
   - Uses fakes: `FakePartExecutor`, `FakePlanningPartExecutorFactory`, `FakePlanFlowConverter`, `FakeFailedToExecutePlanUseCase`

## Design Decisions

- **`maxConversionRetries` as constructor parameter**: Rather than coupling to `IterationConfig.max`, the retry budget is a separate constructor parameter. This allows the caller to configure it independently.
- **Session cleanup responsibility**: `PartExecutorImpl` already kills all sessions internally on completion. `DetailedPlanningUseCaseImpl` does NOT call `removeAllForPart` separately.
- **Full loop restart on PlanConversionException**: Each retry creates a NEW `PartExecutor` via the factory and re-runs the entire planning loop, as specified.

## Tests

All 1088 tests pass (0 failures, 3 skipped for integ tests).
