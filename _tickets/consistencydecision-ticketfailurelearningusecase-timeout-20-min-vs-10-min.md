---
id: nid_oyc7uuw0vc6ws2mtszvwez2nr_E
title: "CONSISTENCY_DECISION: TicketFailureLearningUseCase timeout — 20 min vs 10 min"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:02:33Z
status_updated_iso: 2026-03-18T14:02:33Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [docs, consistency]
---

Two specs disagree on the timeout for the TicketFailureLearning agent:

- `doc/use-case/TicketFailureLearningUseCase.md` (line 99) says **20 minutes**
- `doc/core/NonInteractiveAgentRunner.md` consumer table (line 135) says **10 minutes**

Both are authoritative for their respective scope (TicketFailureLearningUseCase.md for the use case, NonInteractiveAgentRunner.md for the runner). A human decision is needed on the correct value, then the non-authoritative spec should be updated to match.

