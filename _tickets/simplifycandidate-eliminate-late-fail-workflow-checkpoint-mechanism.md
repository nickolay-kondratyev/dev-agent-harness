---
closed_iso: 2026-03-17T16:46:55Z
id: nid_3xmmyqr73lt6c4z4r3lubfaxz_E
title: "SIMPLIFY_CANDIDATE: Eliminate late fail-workflow checkpoint mechanism"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:14:07Z
status_updated_iso: 2026-03-17T16:46:55Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, state-machine, protocol]
---

The late fail-workflow mechanism spans four specs (doc/core/agent-to-server-communication-protocol.md, doc/core/SessionsState.md, doc/core/PartExecutor.md, doc/core/TicketShepherd.md) and adds:
- A `lateFailWorkflow` field on SessionEntry (mutable, once-set-never-cleared)
- Checkpoint logic after EVERY PartResult.Completed in TicketShepherd
- Checkpoint logic at EVERY sub-part transition in PartExecutor (doer->reviewer, between iterations)
- A multi-step halt propagation path (server records -> executor checks -> shepherd checks -> FailedToExecutePlanUseCase)

All of this exists for the rare edge case where an agent signals `done` and then immediately signals `fail-workflow`, retracting its completion. This is defensive programming for a scenario the spec itself acknowledges is unusual.

**Simplification:** If `done` was already received and the CompletableDeferred completed, treat any subsequent `fail-workflow` as a log-only ERROR (same as the existing duplicate-signal handling). The reviewer step already validates the doer output — if the output is actually broken, the reviewer will catch it and signal needs_iteration.

**Robustness improvement:** Removes mutable state from SessionEntry. Eliminates checkpoint logic from every transition point in TicketShepherd and PartExecutor. Simpler state machine = fewer edge cases and less cognitive load. The reviewer is already the safety net for bad output.

**Spec files affected:** doc/core/agent-to-server-communication-protocol.md (remove late fail-workflow section), doc/core/SessionsState.md (remove lateFailWorkflow field and methods), doc/core/PartExecutor.md (remove checkpoint logic), doc/core/TicketShepherd.md (remove checkpoint logic).

--------------------------------------------------------------------------------
OK yes lets simplify and just LOG error as proposed above. Lets document that this is simplification to KISS.

