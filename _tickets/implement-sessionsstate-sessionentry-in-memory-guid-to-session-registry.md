---
id: nid_erd0khe8sg0vqbnwtg23aqzw9_E
title: "Implement SessionsState + SessionEntry — in-memory GUID-to-session registry"
status: open
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E]
links: [nid_v14amda2uv5nedrp9hvb8xlfq_E]
created_iso: 2026-03-19T00:39:38Z
status_updated_iso: 2026-03-19T00:39:38Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, protocol, core]
---

## Context

Spec: `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E)
Protocol spec: `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E)

The in-memory registry of live agent sessions, keyed by `HandshakeGuid`. Bridges the HTTP server (which receives callbacks identified by GUID) with the `CompletableDeferred<AgentSignal>` that the executor is suspended on.

## What to Implement

### 1. SessionEntry data class (ref.ap.igClEuLMC0bn7mDrK41jQ.E)

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/server/SessionEntry.kt`

Fields:
- `tmuxAgentSession: TmuxAgentSession` — live session handle
- `partName: String` — which part this session belongs to
- `subPartName: String` — sub-part name (e.g., "impl", "review")
- `subPartIndex: Int` — position (0 = DOER, 1 = REVIEWER)
- `signalDeferred: CompletableDeferred<AgentSignal>` — completed by server on done/fail-workflow
- `lastActivityTimestamp: Instant` — initialized to registration time, updated by server on every callback
- `pendingPayloadAck: PayloadId?` — set before send-keys, cleared on matching ack-payload
- `questionQueue: ConcurrentLinkedQueue<PendingQuestion>` — thread-safe queue for user-question signals
- `isQAPending: Boolean` (derived property) — `questionQueue.isNotEmpty()`

### 2. SubPartRole enum

```kotlin
enum class SubPartRole {
    DOER, REVIEWER;
    companion object {
        fun fromIndex(index: Int): SubPartRole = when (index) {
            0 -> DOER
            1 -> REVIEWER
            else -> throw IllegalArgumentException("Invalid sub-part index: $index")
        }
    }
}
```

### 3. PendingQuestion data class

```kotlin
data class PendingQuestion(
    val question: String,
    val context: UserQuestionContext,
)
```

### 4. SessionsState interface + implementation

Operations:
- `register(guid: HandshakeGuid, entry: SessionEntry)` — adds or updates
- `lookup(guid: HandshakeGuid): SessionEntry?` — returns entry or null
- `removeAllForPart(partName: String)` — removes all sessions for a part

Backed by coroutine-safe `MutableSynchronizedMap` (suspend-friendly `Mutex`).

### 5. signalDeferred Lifecycle
1. Created by `AgentFacadeImpl` — once on initial spawn, then again at start of every `sendPayloadAndAwaitSignal` call
2. Registered on SessionEntry in SessionsState
3. Completed by server (on done/fail-workflow) or by facade health-aware await loop on crash detection
4. Not exposed to PartExecutor

## Dependencies
- `HandshakeGuid` (already exists at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt`)
- `AgentSignal` sealed class (nid_m7oounvwb31ra53ivu7btoj5v_E)
- `PayloadId` value class (separate ticket)
- `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E — defined in existing code)

## Testing
- Unit test: register + lookup returns entry
- Unit test: lookup unknown GUID returns null
- Unit test: removeAllForPart removes correct entries
- Unit test: SubPartRole.fromIndex(0) = DOER, fromIndex(1) = REVIEWER, other throws
- Unit test: isQAPending derived correctly from questionQueue state


## Notes

**2026-03-19T00:42:42Z**

NOTE: This ticket overlaps with nid_v14amda2uv5nedrp9hvb8xlfq_E which was created from the SessionsState.md spec. The two tickets cover the same component. Implementer should close whichever is picked up second, or merge them into one implementation session. The key difference: this ticket includes SessionEntry fields and SubPartRole enum inline, while the other ticket has those as separate sub-tickets (nid_5o5wyxuzoz7qrkuq4wuo2gnjr_E for SessionEntry, nid_6zpwfuz85gl4x175fcudp9lju_E for SubPartRole).
