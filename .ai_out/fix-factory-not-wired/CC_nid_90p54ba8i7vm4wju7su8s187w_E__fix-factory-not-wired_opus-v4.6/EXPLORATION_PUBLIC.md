# Exploration: Fix Factory Not Wired

## Problem
`SetupPlanUseCaseFactory` at `TicketShepherdCreator.kt:119-121` throws `TODO()` at runtime.

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` — the TODO location
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/SetupPlanUseCase.kt` — `SetupPlanUseCaseImpl` + interface
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/StraightforwardPlanUseCase.kt` — fully constructible
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCase.kt` — needs `PlanningPartExecutorFactory` (no production impl)
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/PlanningPartExecutorFactory.kt` — interface only, no production impl

## Factory Interface
```kotlin
fun interface SetupPlanUseCaseFactory {
    fun create(workflowDefinition: WorkflowDefinition, outFactory: OutFactory): SetupPlanUseCase
}
```

## Fix Strategy
Wire factory to construct `SetupPlanUseCaseImpl` with:
- `StraightforwardPlanUseCaseImpl` — fully constructible from factory args (`wd.parts`, `outFactory`)
- `DetailedPlanningUseCase` — use TODO lambda (can't wire yet, `PlanningPartExecutorFactory` has no production impl)

This pushes the TODO from factory-level to the specific code path that actually requires the missing dependency.
**Straightforward workflows will work. Detailed planning will fail with a precise error.**
