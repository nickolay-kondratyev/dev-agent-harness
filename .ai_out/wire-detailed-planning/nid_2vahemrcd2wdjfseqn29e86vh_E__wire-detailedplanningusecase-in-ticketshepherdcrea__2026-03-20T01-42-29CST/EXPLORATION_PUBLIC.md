# Exploration: Wire DetailedPlanningUseCaseImpl

## Problem
`TicketShepherdCreatorImpl` line 134 has a TODO stub for `DetailedPlanningUseCase` inside `setupPlanUseCaseFactory` default. The blocker (production `PartExecutorFactory` - ticket `nid_l32a31tdfjoe7ld0uurtum9g6_E`) is now **CLOSED/COMPLETED**.

## Key Files

| File | Role |
|------|------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` | TODO stub location (line 134), `SetupPlanUseCaseFactory` interface |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCase.kt` | `DetailedPlanningUseCaseImpl` — needs 6 constructor params |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/PlanningPartExecutorFactory.kt` | Interface — `create(priorConversionErrors): PartExecutor` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/ProductionPartExecutorFactoryCreator.kt` | Pattern for building infra (AgentFacade, ContextForAgentProvider, GitCommitStrategy, etc.) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactoryCreator.kt` | `PartExecutorFactoryContext`, `PartExecutorFactoryCreator.buildFactory()` helper |
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilder.kt` | Builds `SubPartConfig` from `Part` + role defs |
| `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverter.kt` | `PlanFlowConverterImpl(aiOutputStructure, currentStatePersistence, outFactory)` |
| `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt` | Tests using `SetupPlanUseCaseFactory { _, _ -> ... }` |
| `app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCaseImplTest.kt` | `FakePlanningPartExecutorFactory` |
| `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` | Spec (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) |

## DetailedPlanningUseCaseImpl Constructor

```kotlin
class DetailedPlanningUseCaseImpl(
    private val partExecutorFactory: PlanningPartExecutorFactory,  // NEW: needs production impl
    private val planFlowConverter: PlanFlowConverter,               // PlanFlowConverterImpl(aiOutputStructure, currentStatePersistence, outFactory)
    private val failedToExecutePlanUseCase: FailedToExecutePlanUseCase, // Already built in wireTicketShepherd (but AFTER setupPlan — needs reorder)
    private val currentState: CurrentState,                         // stateResult.currentState
    private val maxConversionRetries: Int,                          // constant (3)
    private val outFactory: OutFactory,                             // available
)
```

## Challenge: SetupPlanUseCaseFactory Has Limited Context

Current factory interface: `fun create(workflowDefinition: WorkflowDefinition, outFactory: OutFactory): SetupPlanUseCase`

But `DetailedPlanningUseCaseImpl` needs: `shepherdContext`, `stateResult` (aiOutputStructure, currentState, currentStatePersistence), `ticketData`, `failedToExecutePlanUseCase`, and the planning infra.

**Solution**: Expand `SetupPlanUseCaseFactory` to accept a richer context (similar to `PartExecutorFactoryContext` pattern).

## PlanningPartExecutorFactory Production Impl

Needs to create `PartExecutorImpl` for planning phase. Requires:
- `PartExecutorDeps` (AgentFacade, ContextForAgentProvider, GitCommitStrategy, FailedToConvergeUseCase, OutFactory)
- `SubPartConfigBuilder` (aiOutputStructure, roleDefinitions, ticketContent, planMdPath)
- Planning `Part` from `WorkflowDefinition.planningParts`

The infra building follows the same pattern as `ProductionPartExecutorFactoryCreator` (lines 59-107).

**`priorConversionErrors`**: Interface requires these to be used. For V1, write them to a file in the planning comm directory that gets included in the planner's instruction context.

## Ordering Issue in wireTicketShepherd

Currently: `setupPlanUseCase` (line 247) is created BEFORE `failedToExecutePlanUseCase` (line 249). Need to reorder — move `failedToExecutePlanUseCase` creation before `setupPlanUseCase`.

## Pattern to Follow

`ProductionPartExecutorFactoryCreator` (lines 50-207) shows the complete pattern for building:
1. Role definitions from disk via `RoleCatalogLoader`
2. `AgentFacadeImpl` with sessions, tmux, context window reader
3. `ContextForAgentProvider.standard()`
4. `GitCommitStrategy.commitPerSubPart()`
5. `FailedToConvergeUseCaseImpl`
6. `SubPartConfigBuilder`
7. `PartExecutorDeps` bundle
