---
id: nid_l8qzlu902rjbs0riis8xlwtti_E
title: "SIMPLIFY_CANDIDATE: Eliminate SetupPlanUseCase routing layer — let TicketShepherd decide plan type directly"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:23:10Z
status_updated_iso: 2026-03-17T21:23:10Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, planning, robustness]
---


## Notes

**2026-03-17T21:23:20Z**

SetupPlanUseCase adds a routing layer between TicketShepherd and the two planning use-cases (StraightforwardPlanUseCase / DetailedPlanningUseCase). The caller (TicketShepherd) could simply decide based on workflow type, making the planning path explicit. See specs: doc/use-case/SetupPlanUseCase/__this.md, doc/core/TicketShepherd.md (step 2). Simpler approach: replace SetupPlanUseCase with an inline when-expression in TicketShepherd. Benefits: eliminates one indirection layer, makes planning path visible at the top-level orchestrator, fewer wiring points in TicketShepherdCreator, no loss of robustness.
