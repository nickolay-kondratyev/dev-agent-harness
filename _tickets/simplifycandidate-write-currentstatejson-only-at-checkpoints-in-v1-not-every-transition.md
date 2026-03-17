---
id: nid_j90sxdtb3gjbgzvp1i2tfld8b_E
title: "SIMPLIFY_CANDIDATE: Write current_state.json only at checkpoints in V1 (not every transition)"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:05:10Z
status_updated_iso: 2026-03-17T21:05:10Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, v1, schema, io]
---

Per spec (doc/core/TicketShepherdCreator.md, doc/schema/plan-and-current-state.md), current_state.json is written on EVERY state transition for durability.

However, V1 explicitly does NOT implement resume-on-restart (Resume-on-restart is V2, ref.ap.LX1GCIjv6LgmM7AJFas20.E). This means every write to current_state.json in V1 is wasted I/O relative to the stated V1 scope.

**Opportunity:** In V1, limit writes to key checkpoints:
- After plan is established (initial write)
- After each part completes (COMPLETED state)
- On terminal failure

Do NOT write on every sub-part status transition (IN_PROGRESS → COMPLETED per sub-part, which happens multiple times per part).

Benefit:
- Simpler implementation: no need to track every state machine node as a write trigger
- Less I/O
- Clearer V1/V2 boundary in the code (the "write on every transition" complexity belongs in V2)

The schema itself stays the same — only the write frequency changes. V2 can re-enable frequent writes when resume logic is implemented.

Spec reference: doc/schema/plan-and-current-state.md, ref.ap.LX1GCIjv6LgmM7AJFas20.E (Resume-on-restart V2)

