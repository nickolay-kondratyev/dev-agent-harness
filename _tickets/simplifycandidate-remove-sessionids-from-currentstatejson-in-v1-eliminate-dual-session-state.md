---
id: nid_th5cnl9otrhxkchr802vtn68q_E
title: "SIMPLIFY_CANDIDATE: Remove sessionIds from current_state.json in V1 — eliminate dual session state"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:25:08Z
status_updated_iso: 2026-03-18T14:25:08Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, yagni]
---

## Problem

Session information exists in two places:
1. `SessionsState` — in-memory, keyed by HandshakeGuid, with live TMUX handles and deferreds
2. `current_state.json` — on-disk, `sessionIds` array per sub-part, with handshakeGuid, agentSession.id, agentType, model, timestamp

Both must be kept in sync during spawn operations. The on-disk records exist for V2 resume-on-restart, but V1 has no resume capability. The spec acknowledges these are "independent" in V1.

## Spec Reference

- `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E)
- `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E)
- `doc/use-case/ContextWindowSelfCompactionUseCase.md` — session rotation writes new records

## Proposed Change

Remove the `sessionIds` array from `current_state.json` sub-part schema in V1. The in-memory `SessionsState` is the single source of truth. When V2 resume is implemented, add the on-disk persistence then (alongside the resume logic that actually reads it).

## Justification

- **YAGNI**: V1 never reads `sessionIds` from disk. It is write-only data with no consumer.
- **Eliminates dual-state consistency risk**: No possibility of in-memory/on-disk divergence.
- **Less I/O**: Fewer fields to serialize on every state transition (current_state.json is rewritten on every transition).
- **Easy to reverse**: Adding `sessionIds` back for V2 is straightforward — the data is available in-memory.

