# Wire DetailedPlanningUseCaseImpl in TicketShepherdCreator

## What Was Done

Replaced the TODO stub in `TicketShepherdCreatorImpl.setupPlanUseCaseFactory` with a fully wired
`DetailedPlanningUseCaseImpl` backed by a production `ProductionPlanningPartExecutorFactory`.

### Changes

1. **Created `SetupPlanUseCaseContext`** data class grouping all deps needed by `SetupPlanUseCaseFactory`:
   `workflowDefinition`, `outFactory`, `shepherdContext`, `aiOutputStructure`, `currentState`,
   `currentStatePersistence`, `ticketData`, `failedToExecutePlanUseCase`, `repoRoot`.

2. **Updated `SetupPlanUseCaseFactory`** interface from `fun create(WorkflowDefinition, OutFactory)`
   to `suspend fun create(SetupPlanUseCaseContext)` to provide the richer context.

3. **Created `ProductionPlanningPartExecutorFactory`** implementing `PlanningPartExecutorFactory`:
   - Placed at `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactory.kt`
   - Follows `ProductionPartExecutorFactoryCreator` pattern for building infra (AgentFacade,
     ContextForAgentProvider, GitCommitStrategy, SubPartConfigBuilder, PartExecutorDeps)
   - Suspend companion `create()` factory method builds all infra; non-suspend `create(priorConversionErrors)`
     assembles `PartExecutorImpl`
   - `priorConversionErrors`: logged with println warning when non-empty (V1 limitation; V2 will wire into context)

4. **Updated default wiring** in `TicketShepherdCreatorImpl`:
   - Added `wireDetailedPlanningUseCase()` companion method that builds `ProductionPlanningPartExecutorFactory`,
     `PlanFlowConverterImpl`, and `DetailedPlanningUseCaseImpl`
   - For straightforward workflows (no planningParts), returns a fail-fast stub

5. **Reordered** `failedToExecutePlanUseCase` creation before `setupPlanUseCase` in `wireTicketShepherd()`
   to satisfy dependency ordering.

6. **Updated tests**: `TicketShepherdCreatorTest` factory signature from `{ _, _ -> }` to `{ _ -> }`.

7. **Added unit tests** for `ProductionPlanningPartExecutorFactory` covering doer-only and doer+reviewer parts.

## Files Modified

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` | New `SetupPlanUseCaseContext`, updated factory, reordered deps, `wireDetailedPlanningUseCase()` |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactory.kt` | **NEW** - Production `PlanningPartExecutorFactory` implementation |
| `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt` | Updated for new factory signature |
| `app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactoryTest.kt` | **NEW** - Unit tests |

## Tests

All 1460 tests pass (`./gradlew :app:test`). Detekt clean.

## Decisions

- **`priorConversionErrors` not wired into agent context (V1)**: Logged via println since
  `PlanningPartExecutorFactory.create()` is non-suspend and `Out.warn` is suspend.
  This is a known V1 limitation — V2 should inject errors into the planner's instruction context.
- **Constructor visibility**: `ProductionPlanningPartExecutorFactory` constructor is `internal`
  (not `private`) to enable direct unit testing of `create()` without going through the suspend
  companion factory that requires real infrastructure.
