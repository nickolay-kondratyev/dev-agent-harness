---
id: nid_zgc5ozb1zspazunktkk6fpag7_E
title: "CONSISTENCY_DECISION: AgentFacade unified sendPayload vs separate spawnAgent + sendPayloadAndAwaitSignal"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:19:22Z
status_updated_iso: 2026-03-18T14:19:22Z
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

