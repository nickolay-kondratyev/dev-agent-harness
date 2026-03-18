---
closed_iso: 2026-03-18T14:28:05Z
id: nid_zgc5ozb1zspazunktkk6fpag7_E
title: "CONSISTENCY_DECISION: AgentFacade unified sendPayload vs separate spawnAgent + sendPayloadAndAwaitSignal"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:19:22Z
status_updated_iso: 2026-03-18T14:28:05Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [specs, consistency]
---

ContextWindowSelfCompactionUseCase.md (ref.ap.8nwz2AHf503xwq8fKuLcl.E) references `agentFacade.sendPayload(config, handle, instructions)` — a unified method that transparently spawns a new session when handle is null (session rotation after compaction).

However, AgentFacade.md (ref.ap.9h0KS4EOK5yumssRCJdbq.E) defines separate methods:
- `spawnAgent(config): AgentSessionHandle`
- `sendPayloadAndAwaitSignal(handle, payload): AgentSignal`
- `killSession(handle)`

The compaction spec envisions a combined spawn-if-null + send pattern that does not exist in the current AgentFacade interface.

Options:
1. Update AgentFacade to add a unified `sendPayload(config, handle?, instructions)` method

Files:
- doc/use-case/ContextWindowSelfCompactionUseCase.md
- doc/core/AgentFacade.md

---

## Resolution

**Decision: Updated compaction spec to use the existing AgentFacade API** (Option: keep `spawnAgent` + `sendPayloadAndAwaitSignal` separate, fix the compaction spec).

**Why NOT add a unified `sendPayload` method:**
1. The compaction spec's `sendPayload` separated send from await (returned handle, then `await signal on handle.signal`), which **contradicts** AgentFacade design decision D2 — the facade owns signal lifecycle, no exposed deferreds.
2. The existing API is used consistently across PartExecutor.md, ReInstructAndAwait.md, granular-feedback-loop.md, and all other specs.
3. Adding a unified method grows the AgentFacade interface (ISP concern already flagged in R4).
4. Post-compaction re-spawn reuses the executor's existing first-iteration spawn code path — DRY without a new method.
5. Explicit spawn is more readable (Principle of Least Surprise).

**Changes made to `doc/use-case/ContextWindowSelfCompactionUseCase.md`:**
- Compaction flow pseudocode: `sendPayload` → `sendPayloadAndAwaitSignal`; signal returned directly (no separate await)
- "Impact on PartExecutor" section: rewritten with explicit `spawnAgent` + `sendPayloadAndAwaitSignal` code
- "Session Rotation Detail": explicit spawn + send after compaction
- "Idle Session Death": fixed V1 behavior (AgentCrashed, no auto-respawn) vs V2 (auto-respawn)
- Requirements R7: explicit spawn + send

**No changes needed to `doc/core/AgentFacade.md`** — the interface was already correct.
