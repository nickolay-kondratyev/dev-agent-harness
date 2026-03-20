---
closed_iso: 2026-03-20T00:38:20Z
id: nid_90p54ba8i7vm4wju7su8s187w_E
title: "fix factory not wired"
status: closed
deps: []
links: []
created_iso: 2026-03-20T00:19:44Z
status_updated_iso: 2026-03-20T00:38:20Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


## Resolution
Wired `SetupPlanUseCaseFactory` default in `TicketShepherdCreatorImpl` to construct real `SetupPlanUseCaseImpl`:
- `StraightforwardPlanUseCaseImpl` — fully wired (straightforward workflows now work)
- `DetailedPlanningUseCase` — deferred via TODO (requires `PlanningPartExecutorFactory` production impl)

File changed: `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`

## Original Error
```
kotlin.NotImplementedError: SetupPlanUseCaseFactory not yet wired for production
  at TicketShepherdCreatorImpl._init_$lambda$0(TicketShepherdCreator.kt:120)
```