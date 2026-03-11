---
id: nid_etxturughxixkl5hmvgazco3j_E
title: "V2: Live agent sessions across iterations + inter-agent messaging"
status: open
deps: []
links: []
created_iso: 2026-03-11T14:14:08Z
status_updated_iso: 2026-03-11T14:14:08Z
type: feature
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [v2, sessions, messaging]
---

V1 kills agent sessions between iterations (separate session per sub-part run). V2 should evolve toward:

## Phase 1: Keep sessions alive across iterations
- If the implementor's context window has room, resume the same session instead of spawning fresh.
- Harness decides based on context usage whether to resume or start fresh.
- This is valuable even WITHOUT messaging — avoids cold-start cost and preserves agent memory.

## Phase 2: Inter-agent messaging
- Lightweight message passing between live agents (reviewer <-> implementor) via harness CLI + server routing.
- Key challenge: ingestion ordering — agent must confirm it read message N before receiving N+1.
- Enables faster convergence for small feedback without full iteration cycles.
- Only makes sense once agents persist across iterations (Phase 1).

## Design constraint for V1
- current_state.json sessionIds array already supports multiple sessions per sub-part.
- ContextProvider assembles instructions — can be extended to include message history.
- No V1 code should preclude keeping sessions alive in V2.

