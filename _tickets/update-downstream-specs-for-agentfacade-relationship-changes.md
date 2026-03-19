---
id: nid_go6n8yo6eqokjl0y84ki78aq6_E
title: "Update downstream specs for AgentFacade relationship changes"
status: open
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_p1w49sk0s2isnvcjbmhgapho7_E]
links: []
created_iso: 2026-03-19T00:30:18Z
status_updated_iso: 2026-03-19T00:30:18Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [docs, spec-update, agent-facade]
---

## Context

Spec: `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E), R7.

With AgentFacade established, several downstream specs need updates to reflect the new relationship.

## What to Update

### 1. SessionsState.md (ref.ap.7V6upjt21tOoCFXA7nqNh.E)
- `register` caller: change from "PartExecutor" to "AgentFacadeImpl"
- Add note: "Internal to AgentFacadeImpl; not directly accessed by orchestration layer"
- Note: SessionsState.md already has `#need-tickets` for its own implementation. This ticket is ONLY about the relationship update text.

### 2. PartExecutor.md (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E)
- Dependencies section: verify SessionsState is NOT listed as a PartExecutor dependency
- Verify AgentFacade IS listed as the sole agent-facing dependency
- Note: PartExecutor.md already has `#has-tickets`. These are spec text updates only.

### 3. TicketShepherdCreator.md (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
- Wiring section: verify it documents creating AgentFacadeImpl and passing to executor factories
- Already has `#has-tickets` — this is spec text update only.

### 4. SpawnTmuxAgentSessionUseCase.md (ref.ap.hZdTRho3gQwgIXxoUtTqy.E)
- Add note: "Encapsulated by AgentFacadeImpl.spawnAgent(). Still describes the spawn flow accurately."

### 5. high-level.md
- Minor update to the AgentFacade section if diagram or text references need alignment.

## Verification

- No spec references PartExecutor as a direct user of SessionsState
- All specs consistently describe AgentFacade as the boundary between orchestration and infra
- Remove `#need-tickets` from `doc/core/AgentFacade.md` (replaced with `#has-tickets`)

## Acceptance Criteria

- All listed spec files updated
- Consistent narrative across specs
- `#need-tickets` tag removed from AgentFacade.md, replaced with `#has-tickets`

