# SessionsState / ap.7V6upjt21tOoCFXA7nqNh.E

The in-memory registry of live agent sessions, keyed by `HandshakeGuid`
(ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E). Bridges the HTTP server (which receives
callbacks identified by GUID) with the `CompletableDeferred<AgentSignal>` that the executor
is suspended on.

> **Internal to `AgentFacadeImpl`** (ref.ap.9h0KS4EOK5yumssRCJdbq.E).
> `PartExecutor` does **not** access `SessionsState` directly — all agent operations flow
> through the `AgentFacade` facade. `SessionsState` is registered and queried by
> `AgentFacadeImpl` (for spawn, signal delivery, ACK tracking) and `ShepherdServer`
> (for callback routing). This indirection enables `FakeAgentFacade` to replace the
> entire agent infrastructure layer in unit tests.

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
`UserQuestionHandler` (ref.ap.NE4puAzULta4xlOLh5kfD.E), delivers answer via `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E).
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
| `subPartIndex` | `Int` | Position of this sub-part in the sub-parts array (0 = DOER, 1 = REVIEWER). Role is derived on-the-fly via `SubPartRole.fromIndex(subPartIndex)` — single source of truth, no sync needed. |
| `signalDeferred` | `CompletableDeferred<AgentSignal>` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) | The callback bridge — completed by server on `/signal/done` or `/signal/fail-workflow`, or by the executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) on crash detection. The executor suspends on `.await()`. |
| `lastActivityTimestamp` | `Instant` | **Initialized to registration time** (i.e., spawn time) so the health-aware await loop does not see stale initial values. Updated by the server on **every** callback (signal or query). Read by the executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) to decide when to ping and when to declare crash. Resets the health timeout even during side-channel interactions. |
| `pendingPayloadAck` | `PayloadId?` | Set by the executor before sending a `send-keys` payload (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E). Cleared (set to `null`) by the server when a matching `/signal/ack-payload` arrives. The executor polls this field during the ACK-await phase. `null` means no pending ACK (either no payload sent, or ACK received). |

`SubPartRole` is a two-value enum: `DOER`, `REVIEWER`. Role is derived on-the-fly from
`subPartIndex` via `SubPartRole.fromIndex(subPartIndex)` — position 0 maps to `DOER`,
position 1 maps to `REVIEWER`. Used for `/callback-shepherd/signal/done`
result validation (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) — doers send `completed`, reviewers
send `pass` or `needs_iteration`.

`fromIndex` is the single source of truth for position→role mapping. Future role additions
only require updating `fromIndex()`, not session records or any persisted state.

### signalDeferred Lifecycle

1. **Created by** `AgentFacadeImpl` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — once on initial
   `spawnAgent()`, then again at the start of every `sendPayloadAndAwaitSignal()` call
2. **Registered** on the `SessionEntry` in `SessionsState` (same HandshakeGuid, new deferred
   each time — both on initial spawn and on every iteration/re-instruction)
3. **Completed by** the server (on `/callback-shepherd/signal/done` or
   `/callback-shepherd/signal/fail-workflow`) or by the facade's health-aware await loop
   (ref.ap.QCjutDexa2UBDaKB3jTcF.E) on crash detection — both paths live inside
   `AgentFacadeImpl.sendPayloadAndAwaitSignal`
4. **Not exposed** to `PartExecutor` — the executor receives the resolved `AgentSignal`
   directly as the return value of `sendPayloadAndAwaitSignal`; it never holds a raw
   `Deferred<AgentSignal>` reference

---

## Operations

| Operation | Caller | Description |
|-----------|--------|-------------|
| `register(guid, entry)` | `AgentFacadeImpl` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — called on initial spawn (`spawnAgent`) **and** at the start of every `sendPayloadAndAwaitSignal` call (fresh deferred, same GUID) | Adds or updates a session in the registry |
| `lookup(guid)` | `ShepherdServer` (on every callback) | Returns `SessionEntry` or null. Read-only (except `signalDeferred.complete()` and `lastActivityTimestamp` update). |
| `removeAllForPart(partName)` | `TicketShepherd` (when part completes) | Removes all sessions belonging to a part. For the planning phase, `partName = "planning"` (constant — see ref.ap.P3po8Obvcjw4IXsSUSU91.E). |

---

## Concurrency

Backed by coroutine-safe `MutableSynchronizedMap` (suspend-friendly `Mutex`). V1's serial
execution makes per-operation synchronization sufficient. Revisit for V2 parallel agents.

---

## Ownership

- **Created by** `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — empty on creation
- **Owned by** `AgentFacadeImpl` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — registers entries
  during spawn and iteration, removes sessions on part completion
- **Shared with** `ShepherdServer` — the server holds a reference for `lookup` on incoming callbacks
- **Not accessed by** `PartExecutor` — the executor interacts with agents exclusively through
  the `AgentFacade` interface, which abstracts `SessionsState` away
- Lifecycle: lives for the duration of one ticket's processing. Not persisted (V2 — ref.ap.LX1GCIjv6LgmM7AJFas20.E).

---

## Relationship to current_state.json

`SessionsState` is **runtime-only** (in-memory). `current_state.json`
(ref.ap.56azZbk7lAMll0D4Ot2G0.E) is the persistent record. V1: the two are independent —
`SessionsState` reflects live sessions only. No cross-run rebuild
(V2 — ref.ap.LX1GCIjv6LgmM7AJFas20.E).
