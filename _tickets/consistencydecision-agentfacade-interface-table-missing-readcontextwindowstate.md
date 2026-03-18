---
closed_iso: 2026-03-18T14:53:09Z
id: nid_wihclkx38n9g5acrq0367f544_E
title: "CONSISTENCY_DECISION: AgentFacade interface table missing readContextWindowState"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:48:53Z
status_updated_iso: 2026-03-18T14:53:09Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [spec-consistency]
---

FEEDBACK:
--------------------------------------------------------------------------------
AgentFacade.md (ref.ap.9h0KS4EOK5yumssRCJdbq.E) interface shape table only lists 3 methods:
- spawnAgent
- sendPayloadAndAwaitSignal
- killSession

However, ContextWindowSelfCompactionUseCase.md (ref.ap.8nwz2AHf503xwq8fKuLcl.E) at line 182 calls:
  agentFacade.readContextWindowState(handle)

And AgentFacade.md R6 explicitly states that ContextWindowStateReader is NOT in PartExecutor's constructor but is internal to AgentFacadeImpl — implying it is accessed through the AgentFacade interface.\n\nDecision needed:\n1. Add readContextWindowState to the AgentFacade interface table in doc/core/AgentFacade.md\n2. OR clarify in ContextWindowSelfCompactionUseCase.md that readContextWindowState is accessed through a different path (not AgentFacade interface)\n\nRelevant files:\n- doc/core/AgentFacade.md (interface shape table)\n- doc/use-case/ContextWindowSelfCompactionUseCase.md (line ~182, calls agentFacade.readContextWindowState)


--------------------------------------------------------------------------------

DECISION:
It should be already added double check

## Resolution

**No spec changes needed — the specs are already consistent.**

`readContextWindowState(handle): ContextWindowState` is already present in the AgentFacade interface shape table at `doc/core/AgentFacade.md` (line 77 of the table, 4th method listed). The original feedback that the table "only lists 3 methods" was outdated — the method had already been added.

Verified consistency:
- `doc/core/AgentFacade.md` — interface table lists 4 methods: `spawnAgent`, `sendPayloadAndAwaitSignal`, `readContextWindowState`, `killSession`
- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (line 182) — calls `agentFacade.readContextWindowState(handle)` ✅ matches interface
- `doc/core/AgentFacade.md` R6 — states `ContextWindowStateReader` is internal to `AgentFacadeImpl`, not in PartExecutor's constructor ✅ consistent with it being accessed via `AgentFacade.readContextWindowState()`
- `doc/core/AgentFacade.md` R3 (FakeAgentFacade) — mentions controlling `readContextWindowState` return value ✅ consistent