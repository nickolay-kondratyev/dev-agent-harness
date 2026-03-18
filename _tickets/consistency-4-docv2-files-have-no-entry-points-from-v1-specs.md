---
id: nid_iepdd3efhnkui5rn4esic98en_E
title: "CONSISTENCY: 4 doc_v2/ files have no entry points from V1 specs"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T15:24:50Z
status_updated_iso: 2026-03-18T15:28:53Z
type: chore
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [consistency, spec, docs]
---

$Four doc_v2/ files exist but are not referenced from any V1 spec in doc/:

- doc_v2/FailedToExecutePlanUseCaseV2.md
- doc_v2/be-smart-on-whether-to-continue-or-restart.md
- doc_v2/roll-up-PUBLIC-when-we-exist-part.md
- doc_v2/verification-gate.md

They should be referenced from the relevant V1 specs or from the Linked Documentation table in doc/high-level.md, so they are discoverable by implementors. Each file should have a forward reference from its closest V1 spec (e.g., FailedToExecutePlanUseCaseV2 from TicketShepherd.md or HealthMonitoring.md).

