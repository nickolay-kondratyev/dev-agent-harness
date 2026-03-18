---
closed_iso: 2026-03-18T14:41:04Z
id: nid_udi17gkfnz6r8xzqk7qyjba41_E
title: "CONSISTENCY_DECISION: readContextWindowState missing from AgentFacade interface"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:32:28Z
status_updated_iso: 2026-03-18T14:41:04Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [specs, consistency]
---

ContextWindowSelfCompactionUseCase.md (ref.ap.8nwz2AHf503xwq8fKuLcl.E) at line 183 calls agentFacade.readContextWindowState(handle) — a method not defined in the AgentFacade interface table (doc/core/AgentFacade.md lines 73-77).

Also, AgentFacade.md line 362 (Spec Impact table) says readContextWindowState calls go "through AgentFacade" but the method is missing from the interface definition.

The AgentFacade interface currently defines only: spawnAgent, sendPayloadAndAwaitSignal, killSession.

The executor needs to read context window state at done boundaries for compaction decisions (PartExecutor.md line 244). PartExecutor.md line 471 says the executor never accesses ContextWindowStateReader directly — implying it should go through AgentFacade.

DECISION: Add readContextWindowState(handle): ContextWindowState to the AgentFacade interface + update FakeAgentFacade (R3) to support programmable context state

## Resolution

Added `readContextWindowState(handle): ContextWindowState` to the AgentFacade interface table in `doc/core/AgentFacade.md`. Updated:

1. **AgentFacade.md interface table** — new row with method signature, description (done-boundary compaction decisions), and internal delegation to `ContextWindowStateReader`
2. **AgentFacade.md R3 FakeAgentFacade** — added programmable context state control and interaction verification
3. **AgentFacade.md test pseudocode** — added `fake.onReadContextWindowState` to executor test example
4. **PartExecutor.md Dependencies** — added `readContextWindowState` to the AgentFacade method listing

Cross-spec consistency verified: `high-level.md`, `TicketShepherdCreator.md`, `ContextWindowSelfCompactionUseCase.md` all already referenced context window state reading through the facade — now the interface table matches.

