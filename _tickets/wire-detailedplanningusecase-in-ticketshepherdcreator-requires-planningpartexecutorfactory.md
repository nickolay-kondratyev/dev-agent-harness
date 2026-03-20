---
id: nid_2vahemrcd2wdjfseqn29e86vh_E
title: "Wire DetailedPlanningUseCase in TicketShepherdCreator (requires PlanningPartExecutorFactory)"
status: open
deps: [nid_l32a31tdfjoe7ld0uurtum9g6_E]
links: []
created_iso: 2026-03-20T00:46:08Z
status_updated_iso: 2026-03-20T00:46:08Z
type: feature
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [wiring, production, planning]
---

## Context

`TicketShepherdCreatorImpl` (line 129-131 of `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`) has a TODO stub inside the `setupPlanUseCaseFactory` default:

```kotlin
detailedPlanningUseCase = DetailedPlanningUseCase {
    TODO("DetailedPlanningUseCase not yet wired: requires PlanningPartExecutorFactory production impl")
}
```

## What Needs to Happen

Wire a real `DetailedPlanningUseCaseImpl` inside the `setupPlanUseCaseFactory` lambda. The impl already exists — the blocker is that `PlanningPartExecutorFactory` has no production implementation.

### Implementation Already Exists
- `DetailedPlanningUseCaseImpl` in `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCase.kt`
- It needs a `PlanningPartExecutorFactory` — interface at `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/PlanningPartExecutorFactory.kt`

### Spec
- ref.ap.cJhuVZTkwfrWUzTmaMbR3.E (`doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md`)
- Creates `PartExecutorImpl` configured for PLANNER↔PLAN_REVIEWER iteration loop
- Converts approved `plan_flow.json` to `List<Part>` via `convertAndAppend`
- Handles `PlanConversionException` with retry (validation errors injected as planner context)

### Dependencies
- **Blocked by**: Production `PlanningPartExecutorFactory` impl (which itself depends on `PartExecutorImpl` wiring — see PartExecutorFactory ticket)
- Also needs: `PlanFlowConverter`, `FailedToExecutePlanUseCase`, `CurrentState` access

### Existing Fakes (for reference)
- `FakeDetailedPlanningUseCase` in `app/src/test/kotlin/.../SetupPlanUseCaseImplTest.kt`
- `FakePlanningPartExecutorFactory` in `app/src/test/kotlin/.../DetailedPlanningUseCaseImplTest.kt`

### Acceptance Criteria
- Production `PlanningPartExecutorFactory` impl created
- `DetailedPlanningUseCaseImpl` wired in `setupPlanUseCaseFactory` default in `TicketShepherdCreatorImpl`
- TODO stub removed
- Unit tests for `PlanningPartExecutorFactory` production impl
- `with-planning` workflows can reach the planning phase without hitting TODO

