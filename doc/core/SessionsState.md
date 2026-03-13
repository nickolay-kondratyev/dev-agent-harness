# SessionsState / ap.7V6upjt21tOoCFXA7nqNh.E

The in-memory registry of live agent sessions, keyed by `HandshakeGuid`
(ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E). Bridges the HTTP server (which receives
callbacks identified by GUID) with `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E)
(which is suspended on a `CompletableDeferred<AgentSignal>` waiting for the agent's signal).

---

## Why It Exists

When the server receives `/callback-shepherd/signal/done` with a `handshakeGuid`, it needs to:

1. Find the live `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E)
2. Know the sub-part's **role** (doer vs. reviewer) to validate the `result` field
3. Complete the `signalDeferred` so the suspended `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) resumes

`SessionsState` is this lookup table. It is the **single place** where GUID → session
+ sub-part context is resolved.

The server does **not** route `/callback-shepherd/signal/done` or `/callback-shepherd/signal/fail-workflow`
to `TicketShepherd` directly. Instead, it completes the `CompletableDeferred<AgentSignal>`
(ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) on the `SessionEntry`, which wakes the executor coroutine.
`/callback-shepherd/signal/user-question` is still handled as a side-channel — server delegates to
`UserQuestionHandler` (ref.ap.NE4puAzULta4xlOLh5kfD.E), delivers answer via TMUX `send-keys`.
The executor stays suspended.

---

## Entry Structure

### SessionEntry / ap.igClEuLMC0bn7mDrK41jQ.E

Each registered session carries its identity plus the workflow context needed for
server-side validation and shepherd-side decision making.

| Field | Type | Purpose |
|-------|------|---------|
| `tmuxAgentSession` | `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E — defined in code at `TmuxAgentSession.kt`) | Live session handle (TMUX + resumable session ID with HandshakeGuid) |
| `partName` | `String` | Which part this session belongs to (e.g., `"ui_design"`, `"main"`) |
| `subPartName` | `String` | Sub-part name (e.g., `"impl"`, `"review"`) |
| `subPartRole` | `SubPartRole` | `DOER` or `REVIEWER` — derived from position in sub-parts array (first = DOER, second = REVIEWER) |
| `signalDeferred` | `CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) | The callback bridge — completed by server on `/signal/done` or `/signal/fail-workflow`, or by the executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) on crash detection. The executor suspends on `.await()`. |
| `lastActivityTimestamp` | `Instant` | **Initialized to registration time** (i.e., spawn time) so the health-aware await loop does not see stale initial values. Updated by the server on **every** callback (signal or query). Read by the executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) to decide when to ping and when to declare crash. Resets the health timeout even during side-channel interactions. |
| `pendingPayloadAck` | `PayloadId?` | Set by the executor before sending a `send-keys` payload (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E). Cleared (set to `null`) by the server when a matching `/signal/ack-payload` arrives. The executor polls this field during the ACK-await phase. `null` means no pending ACK (either no payload sent, or ACK received). |
| `lateFailWorkflow` | `LateFailWorkflow?` | Set by the server when `/signal/fail-workflow` arrives for a deferred already completed with `Done` (ref.ap.Bm7kXwVn3pRtLfYdJ9cQz.E). Contains `reason: String` and `receivedAt: Instant`. Read by the executor at checkpoints and by `TicketShepherd` between parts. `null` means no late fail-workflow received. Once set, never cleared — the workflow must halt. |

`SubPartRole` is a two-value enum: `DOER`, `REVIEWER`. Used for `/callback-shepherd/signal/done`
result validation (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) — doers send `completed`, reviewers
send `pass` or `needs_iteration`.

### signalDeferred Lifecycle

1. **Created by** the `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) before spawning the agent
2. **Registered** on the `SessionEntry` in `SessionsState`
3. **Completed by** the server (on `/callback-shepherd/signal/done` or `/callback-shepherd/signal/fail-workflow`)
   or by the executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) on crash detection
4. **Awaited by** the executor — `deferred.await()` suspends the executor coroutine until completion
5. **Replaced** on iteration: when the reviewer signals `needs_iteration`, the executor creates a
   fresh `CompletableDeferred` and re-registers the `SessionEntry` (same HandshakeGuid, new deferred)

---

## Operations

| Operation | Caller | Description |
|-----------|--------|-------------|
| `register(guid, entry)` | `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) (after spawn, and on each iteration with fresh deferred) | Adds or updates a session in the registry |
| `lookup(guid)` | `ShepherdServer` (on every callback) | Returns `SessionEntry` or null. Read-only (except `signalDeferred.complete()` and `lastActivityTimestamp` update). |
| `removeAllForPart(partName)` | `TicketShepherd` (when part completes) | Removes all sessions belonging to a part. For the planning phase, `partName = "planning"` (constant — see ref.ap.P3po8Obvcjw4IXsSUSU91.E). |
| `recordLateFailWorkflow(guid, reason)` | `ShepherdServer` (when `/signal/fail-workflow` arrives for a deferred already completed with `Done` — ref.ap.Bm7kXwVn3pRtLfYdJ9cQz.E) | Sets `lateFailWorkflow` on the `SessionEntry`. Idempotent — if already set, keeps the first (earliest) reason. |
| `checkLateFailWorkflow(partName)` | `PartExecutor`, `TicketShepherd` (at checkpoints) | Returns the first `LateFailWorkflow` found for any session in the given part, or `null`. Used to detect late retractions that arrived after the deferred was completed with `Done`. |

---

## Concurrency

Backed by coroutine-safe `MutableSynchronizedMap` (suspend-friendly `Mutex`). V1's serial
execution makes per-operation synchronization sufficient. Revisit for V2 parallel agents.

---

## Ownership

- **Created by** `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — empty on creation
- **Owned by** `TicketShepherd` — the shepherd removes sessions on part completion; `PartExecutor` registers entries during spawn and iteration
- **Shared with** `ShepherdServer` — the server holds a reference for `lookup` on incoming callbacks
- Lifecycle: lives for the duration of one ticket's processing. Not persisted (V2 — ref.ap.LX1GCIjv6LgmM7AJFas20.E).

---

## Relationship to current_state.json

`SessionsState` is **runtime-only** (in-memory). `current_state.json`
(ref.ap.56azZbk7lAMll0D4Ot2G0.E) is the persistent record. V1: the two are independent —
`SessionsState` reflects live sessions only. No cross-run rebuild
(V2 — ref.ap.LX1GCIjv6LgmM7AJFas20.E).
