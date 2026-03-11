# SessionsState / ap.7V6upjt21tOoCFXA7nqNh.E

The in-memory registry of live agent sessions, keyed by `HandshakeGuid`
(ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E). Bridges the HTTP server (which receives
callbacks identified by GUID) with `TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E)
(which owns workflow context and makes decisions).

---

## Why It Exists

When the server receives `/callback-shepherd/done` with a `handshakeGuid`, it needs to:

1. Find the live `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E)
2. Know the sub-part's **role** (doer vs. reviewer) to validate the `result` field
3. Route the callback to `TicketShepherd` with enough context to decide what happens next

`SessionsState` is this lookup table. It is the **single place** where GUID → session
+ sub-part context is resolved.

---

## Entry Structure

### SessionEntry / ap.igClEuLMC0bn7mDrK41jQ.E

Each registered session carries its identity plus the workflow context needed for
server-side validation and shepherd-side decision making.

| Field | Type | Purpose |
|-------|------|---------|
| `tmuxAgentSession` | `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E) | Live session handle (TMUX + resumable session ID with HandshakeGuid) |
| `partName` | `String` | Which part this session belongs to (e.g., `"ui_design"`, `"main"`) |
| `subPartName` | `String` | Sub-part name (e.g., `"impl"`, `"review"`) |
| `subPartRole` | `SubPartRole` | `DOER` or `REVIEWER` — derived from position in sub-parts array (first = DOER, second = REVIEWER) |

`SubPartRole` is a two-value enum: `DOER`, `REVIEWER`. Used for `/callback-shepherd/done`
result validation (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) — doers send `completed`, reviewers
send `pass` or `needs_iteration`.

---

## Operations

| Operation | Caller | Description |
|-----------|--------|-------------|
| `register(guid, entry)` | `TicketShepherd` (after spawn) | Adds a session to the registry |
| `lookup(guid)` | `ShepherdServer` (on every callback) | Returns `SessionEntry` or null. Read-only. |
| `removeAllForPart(partName)` | `TicketShepherd` (when part completes) | Removes all sessions belonging to a part |

---

## Concurrency Model

### Access Pattern

- **Server** (Ktor coroutine dispatcher): calls `lookup` — **read-only**
- **TicketShepherd** (orchestration coroutine): calls `register`, `removeAllForPart` — **read-write**
- V1: strictly serial execution (one agent at a time), but server callbacks arrive on Ktor's
  dispatcher concurrently with the shepherd's orchestration coroutine.

### Decision: `MutableSynchronizedMap` Is Sufficient

`com.asgard.core.threading.collections.MutableSynchronizedMap<HandshakeGuid, SessionEntry>`
provides per-operation mutex synchronization. This is sufficient because:

1. **`register`** is a single `put` — atomic by itself.
2. **`lookup`** is a single `get` — atomic by itself.
3. **`removeAllForPart`** is multi-step (find entries for part, then remove them) but does
   NOT require atomicity across the steps. Only `TicketShepherd` mutates, and it runs
   serially. The server only reads. A read during removal sees either the old or new state
   — both are valid (the server would get a 400 for an unknown GUID, which is correct
   for a session being torn down).

**No custom mutex needed.** If V2 introduces parallel agents within a part or concurrent
mutations, revisit this decision.

### Why Not a Plain ConcurrentHashMap

`MutableSynchronizedMap` uses coroutine `Mutex` (suspend-friendly). Server callbacks and
shepherd orchestration are both coroutine contexts — blocking a thread with
`ConcurrentHashMap` is unnecessary when a suspend-compatible alternative exists.

---

## Ownership

- **Created by** `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — empty on creation
- **Owned by** `TicketShepherd` — the shepherd registers/removes sessions as it walks the workflow
- **Shared with** `ShepherdServer` — the server holds a reference for `lookup` on incoming callbacks
- Lifecycle: lives for the duration of one ticket's processing. Not persisted — `current_state.json`
  handles persistence; `SessionsState` is rebuilt from it on resume.

---

## Relationship to current_state.json

`SessionsState` is **runtime-only** (in-memory). `current_state.json` (ref.ap.56azZbk7lAMll0D4Ot2G0.E)
is the persistent record. On resume:

1. `TicketShepherdCreator` reads `current_state.json`
2. For in-progress sub-parts, it respawns sessions (via resume flow in ref.ap.hZdTRho3gQwgIXxoUtTqy.E)
3. Each respawned session is registered into a fresh `SessionsState`

The two are never in sync automatically — `TicketShepherd` writes to `current_state.json`
explicitly at checkpoints, while `SessionsState` reflects live sessions only.
