---
id: nid_0o3dqyqe9tlwpi9uroe9tdqpn_E
title: "SIMPLIFY_CANDIDATE: Let AgentFacade own deferred lifecycle — expose sendPayloadAndAwaitSignal suspend API"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:23:21Z
status_updated_iso: 2026-03-17T21:23:21Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, agent-facade, robustness, coroutines]
---


## Notes

**2026-03-17T21:23:50Z**

PartExecutorImpl currently creates a fresh CompletableDeferred on each iteration and manually registers it with SessionsState before calling AgentFacade. Forgetting to reset the deferred is a silent hang bug. See: doc/core/AgentFacade.md, doc/core/PartExecutor.md (re-instruction pattern), doc/core/SessionsState.md. Simpler approach: AgentFacade exposes suspend fun sendPayloadAndAwaitSignal(handle, payload): AgentSignal — internally creates fresh deferred, registers it, sends payload, awaits and returns result. Executor never touches deferreds or SessionsState directly. Benefits: eliminates a category of silent-hang bugs; executor code shrinks significantly; facade fully encapsulates signal lifecycle (aligns with existing seam design ref.ap.9h0KS4EOK5yumssRCJdbq.E).
