---
closed_iso: 2026-03-17T21:49:17Z
id: nid_0o3dqyqe9tlwpi9uroe9tdqpn_E
title: "SIMPLIFY_CANDIDATE: Let AgentFacade own deferred lifecycle — expose sendPayloadAndAwaitSignal suspend API"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:23:21Z
status_updated_iso: 2026-03-17T21:49:17Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, agent-facade, robustness, coroutines]
---


## Notes

**2026-03-17T21:23:50Z**

PartExecutorImpl currently creates a fresh CompletableDeferred on each iteration and manually registers it with SessionsState before calling AgentFacade. Forgetting to reset the deferred is a silent hang bug. See: doc/core/AgentFacade.md, doc/core/PartExecutor.md (re-instruction pattern), doc/core/SessionsState.md.


Simpler approach: AgentFacade exposes suspend fun sendPayloadAndAwaitSignal(handle, payload): AgentSignal — internally creates fresh deferred, registers it, sends payload, awaits and returns result. Executor never touches deferreds or SessionsState directly. 


Benefits: eliminates a category of silent-hang bugs; executor code shrinks significantly; facade fully encapsulates signal lifecycle (aligns with existing seam design ref.ap.9h0KS4EOK5yumssRCJdbq.E).

**2026-03-17T21:49:29Z**

## Resolution

Updated specs (no code changes — spec-only task):

### AgentInteraction.md (AgentFacade spec)
- **Resolved R1** (Re-instruction pattern): chose `sendPayloadAndAwaitSignal` — the method creates a fresh CompletableDeferred, re-registers the SessionEntry, sends payload with ACK, runs the health-aware await loop, and returns the AgentSignal. Executor never sees deferreds.
- **Interface shrinks from 5 to 3 methods**: `spawnAgent`, `sendPayloadAndAwaitSignal`, `killSession`. Removed separate `sendPayloadWithAck`, `sendHealthPing`, `readContextWindowState` (now internal to `sendPayloadAndAwaitSignal`).
- **SpawnedAgentHandle**: removed `signal: Deferred<AgentSignal>` — no longer exposed to executor.
- **Clock + HarnessTimeoutConfig** move to AgentFacadeImpl constructor (not PartExecutorImpl).
- **FakeAgentFacade** simplified: controls what signal `sendPayloadAndAwaitSignal` returns; no need to control ping/context-window separately.

### PartExecutor.md
- Health-aware await loop (ap.QCjutDexa2UBDaKB3jTcF.E) is now documented as owned by AgentFacadeImpl.sendPayloadAndAwaitSignal.
- Re-Instruction Pattern simplified: removed deferred creation/re-registration steps — facade handles them.
- Flow descriptions, PUBLIC.md re-instruction, doer-only path, and ownership note all updated.
- Dependencies section: removed Clock, AgentUnresponsiveUseCase, ContextWindowStateReader from PartExecutorImpl.

### SessionsState.md
- signalDeferred lifecycle: deferred now created on both `spawnAgent` AND every `sendPayloadAndAwaitSignal` call.
- Operations table: `register` caller description updated accordingly.
