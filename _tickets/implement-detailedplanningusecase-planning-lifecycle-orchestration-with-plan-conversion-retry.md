---
id: nid_ddgriql9mr7ry2ugamptxdadm_E
title: "Implement DetailedPlanningUseCase — planning lifecycle orchestration with plan conversion retry"
status: open
deps: [nid_5z93biuqub3mhcejfpofjmj39_E, nid_wppjbc4te6exn13bo3o0jln6n_E, nid_foubbnsh3vmk1fk34zm75zkg0_E, nid_m7oounvwb31ra53ivu7btoj5v_E]
links: []
created_iso: 2026-03-19T00:49:05Z
status_updated_iso: 2026-03-19T00:49:05Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [use-case, planning, detailed-planning]
---

## Context

Spec: `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E)
Also: `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E)

Called by `SetupPlanUseCase` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) for `with-planning` workflows.
Returns `List<Part>` — the caller has no knowledge of the two-phase protocol.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/usecase/DetailedPlanningUseCase.kt`

### Interface + Implementation

```kotlin
interface DetailedPlanningUseCase {
    suspend fun execute(): List<Part>
}
```

### Constructor Dependencies

- `PartExecutorImpl` factory or builder (to create planning executor with PLANNER + PLAN_REVIEWER)
- `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) — shared provider for instruction assembly
- `convertPlanToExecutionParts` function/class (from nid_wppjbc4te6exn13bo3o0jln6n_E)
- `FailedToExecutePlanUseCase` (from nid_foubbnsh3vmk1fk34zm75zkg0_E)
- `AgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — for `removeAllForPart` to kill planning TMUX sessions
- Planning iteration budget (max retries for PlanConversionException loop)

### Core Orchestration (spec steps 1-6)

1. **Create planning executor**: Build a `PartExecutorImpl` configured with reviewer for the PLANNER<>PLAN_REVIEWER iteration loop. Doer role = PLANNER, Reviewer role = PLAN_REVIEWER. Uses `ContextForAgentProvider` with `PlannerRequest` / `PlanReviewerRequest` sealed subtypes.

2. **Run planning executor**: Call `planningExecutor.execute()` which returns `PartResult`.

3. **Handle PartResult**:
   - `Completed` -> proceed to step 4
   - `FailedWorkflow`, `FailedToConverge`, `AgentCrashed` -> delegate to `FailedToExecutePlanUseCase(partResult)` (red error, kills all sessions, exits non-zero)

4. **Kill TMUX sessions**: Call `removeAllForPart` on the planning part. Planning sessions are no longer needed after convergence.

5. **Convert plan to execution parts**: Call `convertPlanToExecutionParts()` which reads `harness_private/plan_flow.json`, validates, appends execution parts to in-memory CurrentState, deletes plan_flow.json, returns `List<Part>`.

6. **PlanConversionException retry loop**:
   - On `PlanConversionException`: log WARN with validation errors
   - Restart the planning loop (step 1-5) with validation errors injected as planner context
   - Each restart counts against the planning iteration budget
   - If budget exhausted: delegate to `FailedToExecutePlanUseCase` (exits non-zero)
   - This is the retry loop that wraps the entire plan-execute-convert sequence

7. **Return** `List<Part>` — the execution-ready parts.

### Planning Sub-Parts Follow Execution Semantics

The planning phase reuses `PartExecutorImpl` identically to execution parts:
- Same `CompletableDeferred<AgentSignal>` callback bridge
- Same TMUX session lifecycle (sessions kept alive across iterations)
- Same iteration budget enforcement (`iteration.max`)
- Same health monitoring (timeout -> ping -> crash detection)
- Same `PartResult` outcomes

The only difference is the instruction content (`PlannerRequest` / `PlanReviewerRequest` vs execution request types).

## Testing

Unit tests with `FakeAgentFacade` covering:
- Happy path: planning converges -> plan converts successfully -> returns execution parts
- Planning executor failure (FailedWorkflow, FailedToConverge, AgentCrashed) -> delegates to FailedToExecutePlanUseCase
- PlanConversionException on first attempt -> retry succeeds -> returns execution parts
- PlanConversionException with validation errors injected as planner context on retry
- Budget exhaustion after repeated PlanConversionException -> FailedToExecutePlanUseCase
- TMUX sessions killed (removeAllForPart called) after planning convergence
- removeAllForPart called BEFORE convertPlanToExecutionParts (ordering matters)

## Spec References

- DetailedPlanningUseCase spec: ref.ap.cJhuVZTkwfrWUzTmaMbR3.E
- PartExecutorImpl: ref.ap.mxIc5IOj6qYI7vgLcpQn5.E
- AgentFacade: ref.ap.9h0KS4EOK5yumssRCJdbq.E
- ContextForAgentProvider: ref.ap.9HksYVzl1KkR9E1L2x8Tx.E
- Plan schema: ref.ap.56azZbk7lAMll0D4Ot2G0.E
- Agent type & model assignment: ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E

