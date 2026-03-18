---
closed_iso: 2026-03-18T13:42:53Z
id: nid_trmjhura2kt236acs7w7p765e_E
title: "SIMPLIFY_CANDIDATE: Fail hard on PlanConversionException — remove planning loop restart"
status: closed
deps: []
links: []
created_iso: 2026-03-17T23:54:36Z
status_updated_iso: 2026-03-18T13:42:53Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec-change]
---

In `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E), when `convertPlanToExecutionParts()` throws `PlanConversionException` (malformed/invalid `plan_flow.json`), the use case restarts the entire planning loop — injecting validation errors as planner context and counting against the iteration budget.

**Problem**: The restart-on-conversion-failure adds significant complexity:
- Try/catch around `convertPlanToExecutionParts()` with special handling for `PlanConversionException`
- Logic to inject validation errors back into planner context for the retry
- Budget accounting for conversion failures (counting against iteration budget)
- The PLAN_REVIEWER already receives `plan_flow.json` content (section 3 of plan reviewer instructions per `doc/core/ContextForAgentProvider.md`) and is specifically tasked with validating it. If the reviewer approved invalid JSON, that indicates a failure in the review criteria, not a transient error that retrying will fix.
- The planner receives the plan format instructions (JSON schema) in its initial instructions — it has full knowledge of what valid JSON looks like. Retrying gives it the same schema plus a validation error, which is marginally more context.

**Simplification**: Fail hard on `PlanConversionException` — treat it as a fundamental planning failure. Delegate to `FailedToExecutePlanUseCase` (red error, halt). The human reviews the invalid `plan_flow.json` and either fixes the review criteria or retries manually.

**What changes**:
- `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` — `convertPlanToExecutionParts()` failure → delegate to `FailedToExecutePlanUseCase` directly. Remove the restart logic, validation error injection, and budget accounting for conversion failures.
- `DetailedPlanningUseCase` becomes simpler: run executor → on Completed → convert plan → return parts. No try/catch/restart loop.

**Robustness improvement**: Forces investment in better review criteria and planner instructions rather than masking review failures with retries. The root cause of conversion failure is always "the plan was invalid" — retrying without fixing the root cause is unlikely to help. If the review criteria are good, conversion failures should be rare; if they are bad, retrying wastes time and iteration budget.

