---
closed_iso: 2026-03-17T19:57:11Z
id: nid_0v5md2o3evkrt31x57eapl0yc_E
title: "SIMPLIFY_CANDIDATE: Remove plan validation query endpoint — validate harness-side only"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:14:29Z
status_updated_iso: 2026-03-17T19:57:11Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, protocol, planning, validation]
---

Plan validation currently happens at THREE levels:
1. Planner agent validates via `/callback-shepherd/query/validate-plan` callback before signaling done
2. Plan reviewer agent validates via the same callback before signaling pass
3. `convertPlanToExecutionParts()` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E, doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md) validates again in the harness

The spec itself acknowledges this is belt-and-suspenders: "this should not happen in practice" for a failure at step 3, calling it "a bug in the planning agents."

The query endpoint adds:
- A Tier 2 (query) HTTP endpoint on ShepherdServer
- A `callback_shepherd.query.sh validate-plan` script command
- Agent instructions about when and how to call the validation endpoint
- The callback_shepherd.query.sh script itself (separate from the signal script)
- Server-side plan parsing and schema validation logic (duplicated with convertPlanToExecutionParts)

**Simplification:** Remove the `/validate-plan` query endpoint entirely. Do validation ONLY in `convertPlanToExecutionParts()` — single source of truth. If the plan is invalid, the conversion fails, PartExecutor returns FailedWorkflow, and the planning loop retries (or exhausts budget). OK lets make sure we have a SOLID pathway for refeeding the failure to the Planner. And we log a WARN if this happens. 

**Robustness improvement:** Single validation point = no drift between what agents validate and what the harness validates. Eliminates the possibility of agents passing their own validation but failing harness validation (or vice versa). Simpler agent instructions.

**Spec files affected:** doc/core/agent-to-server-communication-protocol.md (remove validate-plan section), doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md (simplify validation description), doc/core/ContextForAgentProvider.md (remove validate-plan from agent instructions).


## Notes

**2026-03-17T19:57:07Z**

Resolved by updating spec files only (ticket says focus on spec, not code). Changes made:

1. doc/core/agent-to-server-communication-protocol.md: Removed /validate-plan query endpoint from tables, removed entire 'Plan Validation Query' section, removed callback_shepherd.query.sh script/section, simplified Two-Tier design to signal-only design, updated architecture diagram and all cross-references.

2. doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md: Added rule (f) to convertPlanToExecutionParts validation (role file existence check). Updated failure behavior from 'halt immediately' to 'log WARN, restart planning loop with error injected as planner context, halt only on budget exhaustion'.

3. doc/core/ContextForAgentProvider.md: Removed validate-plan query instruction from Planner (row 11) and Plan Reviewer (row 10) sections. Removed Queries block from the compaction-survival callback help text. Updated footer.

4. doc/high-level.md: Updated architecture description, removed query tier from design decisions table, updated callback scripts row.

5. doc/use-case/HealthMonitoring.md: Removed validate-plan from liveness callback list.

6. doc/schema/plan-and-current-state.md: Updated loadsPlan field doc to reference harness-side validation instead of validate-plan endpoint.

7. doc/core/PartExecutor.md: Removed paragraph describing validate-plan as Tier 2 query endpoint.

8. doc/core/TicketShepherd.md: Updated convertPlanToExecutionParts description to reflect new retry-on-failure behavior.
