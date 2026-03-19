---
id: nid_i28ll9uk8k9uvdv3gus2izj4o_E
title: "Add self-compacted signal endpoint + ProtocolVocabulary update"
status: in_progress
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_v14amda2uv5nedrp9hvb8xlfq_E]
links: []
created_iso: 2026-03-19T00:41:10Z
status_updated_iso: 2026-03-19T19:12:01Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, compaction]
---

## Context

Spec: `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E), R4.
Signal spec: ap.HU6KB4uRDmOObD54gdjYs.E

Adds the `/callback-shepherd/signal/self-compacted` endpoint and updates ProtocolVocabulary.

## What to Implement

### 1. ProtocolVocabulary update

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt`

Add to `Signal` object:
```kotlin
const val SELF_COMPACTED = "self-compacted"
```

### 2. Server route for self-compacted signal

When the callback server is implemented, it must route `/callback-shepherd/signal/self-compacted`:
- Payload: `{ "handshakeGuid": "handshake.xxx" }`
- Server behavior: lookup session in SessionsState → complete `signalDeferred` with `AgentSignal.SelfCompacted`
- Updates `lastActivityTimestamp` (same as other signals)

Note: `AgentSignal.SelfCompacted` is already defined in the AgentFacade interface ticket (nid_m7oounvwb31ra53ivu7btoj5v_E). This ticket adds the server routing.

If the callback server does not yet exist when this ticket is picked up, the scope is:
1. Add `SELF_COMPACTED` to ProtocolVocabulary
2. Add signal routing logic in whatever routing/dispatch mechanism exists (may be a handler map, a when branch, etc.)
3. Ensure the signal-to-AgentSignal mapping handles `self-compacted` → `AgentSignal.SelfCompacted`

### 3. Strict enforcement during compaction

During compaction, `AgentSignal.SelfCompacted` is the ONLY valid success signal. If the agent signals `done` instead → immediate `AgentCrashed("agent cannot follow compaction protocol")`. This enforcement lives in `performCompaction()` (separate ticket), but the signal routing must correctly map self-compacted.

## Tests

1. Unit test: signal dispatch maps "self-compacted" to `AgentSignal.SelfCompacted`
2. Unit test: `lastActivityTimestamp` updated on self-compacted signal
3. Verify `ProtocolVocabulary.Signal.SELF_COMPACTED` constant exists and equals "self-compacted"

## Acceptance Criteria

- ProtocolVocabulary updated
- Signal routing handles self-compacted
- `./test.sh` passes

