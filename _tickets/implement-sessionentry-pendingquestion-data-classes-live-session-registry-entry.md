---
id: nid_5o5wyxuzoz7qrkuq4wuo2gnjr_E
title: "Implement SessionEntry + PendingQuestion data classes — live session registry entry"
status: open
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_6zpwfuz85gl4x175fcudp9lju_E]
links: []
created_iso: 2026-03-19T00:39:41Z
status_updated_iso: 2026-03-19T00:39:41Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
parent: nid_p1w49sk0s2isnvcjbmhgapho7_E
tags: [shepherd, sessions-state]
---

## Context

Spec: `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E), section "Entry Structure" (ap.igClEuLMC0bn7mDrK41jQ.E).

Each registered session in `SessionsState` carries identity plus workflow context needed for server-side validation and shepherd-side decision making.

## What to Implement

### 1. PendingQuestion data class

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/session/PendingQuestion.kt`

```kotlin
data class PendingQuestion(
    val question: String,
    val context: UserQuestionContext,
)
```

`UserQuestionContext` — check `doc/core/UserQuestionHandler.md` (ref.ap.NE4puAzULta4xlOLh5kfD.E) for its shape. If not yet defined in code, define it here as a simple data class carrying whatever context the agent sends with the question (likely: `partName`, `subPartName`, `handshakeGuid`). Keep minimal for V1.

### 2. SessionEntry data class

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt`

```kotlin
data class SessionEntry(
    val tmuxAgentSession: TmuxAgentSession,  // ref.ap.DAwDPidjM0HMClPDSldXt.E — already in code
    val partName: String,
    val subPartName: String,
    val subPartIndex: Int,
    val signalDeferred: CompletableDeferred<AgentSignal>,  // ref.ap.UsyJHSAzLm5ChDLd0H6PK.E
    val lastActivityTimestamp: Instant,  // mutable — updated by server on every callback
    val pendingPayloadAck: PayloadId?,  // set before send-keys, cleared on /signal/ack-payload
    val questionQueue: ConcurrentLinkedQueue<PendingQuestion>,  // server appends, executor drains
) {
    /** Derived: true when questions are queued but not yet fully answered. */
    val isQAPending: Boolean get() = questionQueue.isNotEmpty()

    /** Derive role on-the-fly from subPartIndex — single source of truth. */
    val role: SubPartRole get() = SubPartRole.fromIndex(subPartIndex)
}
```

### 3. PayloadId value class

If not already defined, create:
```kotlin
@JvmInline
value class PayloadId(val value: String)
```

Per spec (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E), PayloadId uses HandshakeGuid prefix + sequence counter.

## Key Design Points

- `lastActivityTimestamp` is initialized to registration time (spawn time) so health-aware await loop does not see stale initial values
- `isQAPending` is a derived property — no separate flag to sync
- `role` is derived on-the-fly via `SubPartRole.fromIndex(subPartIndex)` — never stored
- `signalDeferred` is created fresh on initial spawn AND at start of every `sendPayloadAndAwaitSignal` call
- `questionQueue` uses `ConcurrentLinkedQueue` — thread-safe, server appends, executor drains
- `pendingPayloadAck` is nullable: `null` means no pending ACK

## Dependencies
- `TmuxAgentSession` — already exists in code (ref.ap.DAwDPidjM0HMClPDSldXt.E)
- `AgentSignal` — from ticket nid_m7oounvwb31ra53ivu7btoj5v_E
- `SubPartRole` — from ticket nid_6zpwfuz85gl4x175fcudp9lju_E

## Tests (BDD/DescribeSpec)

- GIVEN SessionEntry with empty questionQueue THEN isQAPending is false
- GIVEN SessionEntry with non-empty questionQueue THEN isQAPending is true
- GIVEN SessionEntry with subPartIndex 0 THEN role is DOER
- GIVEN SessionEntry with subPartIndex 1 THEN role is REVIEWER
- GIVEN SessionEntry WHEN question added to queue THEN isQAPending becomes true
- GIVEN SessionEntry with questions WHEN queue drained THEN isQAPending becomes false

## Package
`com.glassthought.shepherd.core.session`

## Acceptance Criteria
- SessionEntry, PendingQuestion, PayloadId compile
- Derived properties (isQAPending, role) work correctly
- Unit tests verify derived property behavior
- `./test.sh` passes

