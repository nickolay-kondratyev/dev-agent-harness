---
id: nid_7uh4ju2nel6sqqyuhqrp8ouny_E
title: "Implement DetailedPlanningUseCase — planning lifecycle + plan-to-execution-parts conversion"
status: open
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_g3e2bkvqepf2wzipadayt9in8_E, nid_5z93biuqub3mhcejfpofjmj39_E, nid_foubbnsh3vmk1fk34zm75zkg0_E, nid_m3cm8xizw5qhu1cu3454rca79_E, nid_wppjbc4te6exn13bo3o0jln6n_E]
links: []
created_iso: 2026-03-19T00:50:57Z
status_updated_iso: 2026-03-19T00:50:57Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, planning, use-case]
---

## Context

Spec: `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E)

Owns the full planning lifecycle for `with-planning` workflows. Called by `SetupPlanUseCase` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E).

## What to Implement

### 1. DetailedPlanningUseCase

```kotlin
class DetailedPlanningUseCase(
    private val agentFacade: AgentFacade,
    private val contextForAgentProvider: ContextForAgentProvider,
    private val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
    private val currentState: CurrentState,
    private val iterationConfig: IterationConfig,
    private val outFactory: OutFactory,
) {
    suspend fun execute(): List<Part>
}
```

### 2. Main Flow

1. Create PartExecutorImpl (with reviewer) for PLANNER↔PLAN_REVIEWER iteration loop
2. Run `planningExecutor.execute()` → `PartResult`
3. Handle PartResult:
   - `Completed` → proceed to plan conversion
   - Any failure → delegate to `FailedToExecutePlanUseCase`
4. Kill TMUX sessions for planning part (`removeAllForPart`)
5. Call `convertPlanToExecutionParts()` → `List<Part>`
   - On `PlanConversionException`: log WARN, restart planning loop with validation errors as context. Counts against iteration budget.
6. Return execution parts

### 3. convertPlanToExecutionParts()

1. Read plan_flow.json from harness_private/plan_flow.json
2. Validate against parts/sub-parts schema (ref.ap.56azZbk7lAMll0D4Ot2G0.E)
3. Append execution parts to in-memory CurrentState; flush to disk
4. Delete plan_flow.json
5. Return List<Part>

If malformed → PlanConversionException (extends AsgardBaseException)

### 4. PlanConversionException

Thrown by convertPlanToExecutionParts when plan_flow.json fails schema validation.

## Dependencies
- AgentFacade (nid_m7oounvwb31ra53ivu7btoj5v_E)
- FakeAgentFacade for testing (nid_g3e2bkvqepf2wzipadayt9in8_E)
- PartExecutorImpl (nid_5z93biuqub3mhcejfpofjmj39_E) — used to run planning loop
- FailedToExecutePlanUseCase (nid_foubbnsh3vmk1fk34zm75zkg0_E)
- CurrentState + Part data classes (nid_m3cm8xizw5qhu1cu3454rca79_E)
- plan_flow.json parsing (nid_wppjbc4te6exn13bo3o0jln6n_E)

## Testing (via FakeAgentFacade)
- Happy path: planning completes → plan_flow.json valid → execution parts returned
- Planning failure: PartResult.FailedWorkflow → delegates to FailedToExecutePlanUseCase
- Plan validation failure: PlanConversionException → restarts planning loop with error context
- Plan validation failure exhausts budget → FailedToExecutePlanUseCase
- TMUX sessions killed after planning completes

## Acceptance Criteria
- execute() returns List<Part> for valid plans
- PlanConversionException triggers retry with error injection
- Budget exhaustion handled via FailedToExecutePlanUseCase
- All unit tests pass using FakeAgentFacade
- `./test.sh` passes

