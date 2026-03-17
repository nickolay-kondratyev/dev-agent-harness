---
id: nid_0v5md2o3evkrt31x57eapl0yc_E
title: "SIMPLIFY_CANDIDATE: Remove plan validation query endpoint — validate harness-side only"
status: in_progress
deps: []
links: []
created_iso: 2026-03-15T01:14:29Z
status_updated_iso: 2026-03-17T19:50:27Z
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

