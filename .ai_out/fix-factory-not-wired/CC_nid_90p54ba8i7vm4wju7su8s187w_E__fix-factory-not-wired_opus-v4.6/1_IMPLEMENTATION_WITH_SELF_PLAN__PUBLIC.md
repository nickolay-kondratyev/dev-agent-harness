# Implementation: Wire SetupPlanUseCaseFactory

## What Was Done
Replaced the `TODO()` default for `SetupPlanUseCaseFactory` in `TicketShepherdCreatorImpl` with a real implementation that constructs `SetupPlanUseCaseImpl` with `StraightforwardPlanUseCaseImpl`.

The `DetailedPlanningUseCase` remains a TODO since `PlanningPartExecutorFactory` has no production implementation yet. This narrows the failure surface from "all workflows fail" to "only detailed-planning code path fails with a precise error."

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`
  - Added 3 imports: `DetailedPlanningUseCase`, `SetupPlanUseCaseImpl`, `StraightforwardPlanUseCaseImpl`
  - Replaced TODO lambda with real factory wiring

## Tests
- All existing tests pass (`./gradlew :app:test` exit code 0)
- Existing tests use stubs/fakes for the factory so they are unaffected by this change
