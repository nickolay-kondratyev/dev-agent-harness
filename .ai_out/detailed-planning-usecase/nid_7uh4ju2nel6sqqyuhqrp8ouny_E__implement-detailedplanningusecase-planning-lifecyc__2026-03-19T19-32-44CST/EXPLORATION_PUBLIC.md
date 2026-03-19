# Exploration: DetailedPlanningUseCase Implementation

## Spec Location
- `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E)

## What Already Exists
- `PlanFlowConverter` + `PlanFlowConverterImpl` — reads plan_flow.json, validates, appends to CurrentState, flushes, deletes file.
  `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverter.kt`
- `PlanConversionException` — extends AsgardBaseException.
  `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt`
- `PartExecutorImpl` — full doer+reviewer iteration loop.
  `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`
- `PartExecutorDeps` — deps bundle with AgentFacade, ContextForAgentProvider, GitCommitStrategy, FailedToConvergeUseCase, OutFactory.
- `SubPartConfig` — static configuration for spawning sub-parts.
  `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfig.kt`
- `FailedToExecutePlanUseCase` — prints red error, kills all sessions, exits non-zero.
  `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCase.kt`
- `CurrentState` — in-memory state with `appendExecutionParts()`.
  `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt`
- `FakeAgentFacade` — programmable test double.
  `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt`

## What Needs to Be Created
1. `DetailedPlanningUseCase` (interface + impl) in `com.glassthought.shepherd.usecase.planning`
2. Unit tests using FakeAgentFacade and testing patterns from PartExecutorImplTest

## Key Dependencies
| Dependency | Interface | Location |
|-----------|-----------|----------|
| AgentFacade | `AgentFacade` | `.../core/agent/facade/AgentFacade.kt` |
| ContextForAgentProvider | `ContextForAgentProvider` | `.../core/context/ContextForAgentProvider.kt` |
| FailedToExecutePlanUseCase | `FailedToExecutePlanUseCase` | `.../usecase/healthmonitoring/FailedToExecutePlanUseCase.kt` |
| CurrentState | data class | `.../core/state/CurrentState.kt` |
| IterationConfig | data class | `.../core/state/IterationConfig.kt` |
| PlanFlowConverter | `PlanFlowConverter` | `.../core/state/PlanFlowConverter.kt` |
| PartExecutorImpl | class | `.../core/executor/PartExecutorImpl.kt` |
| PartExecutorDeps | data class | `.../core/executor/PartExecutorImpl.kt` |

## Data Structures
- `Part` — name, phase (PLANNING/EXECUTION), description, subParts
- `SubPart` — name, role, agentType, model, status, iteration, sessionIds
- `PartResult` — sealed: Completed, FailedWorkflow, FailedToConverge, AgentCrashed
- `IterationConfig` — max, current (default 0)
- `SubPartRole` — DOER, REVIEWER
- `Phase` — PLANNING, EXECUTION

## Testing Patterns
- Extend `AsgardDescribeSpec`
- BDD with describe/it blocks
- Use FakeAgentFacade with programmed handlers
- Create temp PUBLIC.md files for validation
- One assertion per `it` block
- Use `outFactory` inherited from AsgardDescribeSpec
