#has-tickets
# Agent-to-Server Communication Protocol / ap.wLpW8YbvqpRdxDplnN7Vh.E

Defines the full communication protocol between agents running in TMUX sessions and the Shepherd harness server — identity, discovery, endpoints, payloads, and CLI tooling.

---

> Vocabulary: see [high-level.md Vocabulary](../high-level.md#vocabulary).

---

## Architecture

```
┌────────────┐  HTTP POST (curl)      ┌──────────────────┐
│  Agent      │ ──────────────────→   │  Harness Server   │
│  (in TMUX)  │  callback_shepherd.   │  (Ktor CIO)       │
│             │  signal.sh            │                    │
│             │ ←──────────────────   │                    │
│             │   TMUX send-keys      │                    │
└────────────┘                        └──────────────────┘
```

Communication is **bidirectional** through two distinct channels:

- **Agent → Harness**: HTTP POST via `callback_shepherd.signal.sh`. The script handles port discovery and GUID injection transparently.
- **Harness → Agent**: Two channels. **Bootstrap** is delivered as an initial prompt argument in the CLI start command (atomically on startup). All **post-bootstrap** communication uses TMUX `send-keys` — instructions, Q&A answers, health pings, and iteration feedback.

---

## Signal Endpoint Design

Agent-to-harness HTTP endpoints are **fire-and-forget signals** (`/callback-shepherd/signal/*`).

**Fire-and-forget.** The agent POSTs, gets a bare `200 OK`, and moves on. If the harness needs to deliver content back (Q&A answers, iteration feedback, pings), it uses TMUX `send-keys`. Agents that need a response after a signal (e.g., after `user-question`) must **wait** for it to arrive via TMUX, not via the HTTP response.

Signals either complete `CompletableDeferred<AgentSignal>` (lifecycle signals: `done`, `fail-workflow`) or update `lastActivityTimestamp` only (side-channel signals: `started`, `user-question`, `ack-payload`). The `user-question` signal additionally appends the question to `SessionEntry.questionQueue` (ref.ap.NE4puAzULta4xlOLh5kfD.E) — the executor's health-aware await loop picks it up and handles answer collection + delivery. The `ack-payload` signal additionally clears `pendingPayloadAck` on the `SessionEntry` (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).

---

## HandshakeGuid — Agent Identity Key

### Problem

The server needs to know **which agent** is calling on every callback (`/callback-shepherd/signal/done`,
`/callback-shepherd/signal/user-question`, etc.). In a single-agent world this is trivial, but the design must
be multi-agent ready — multiple agents alive at the same time (e.g., implementor and
reviewer talking to each other).

### Solution

Every agent session gets a **HandshakeGuid** — a harness-generated identifier with format
`handshake.${UUID}`. The `handshake.` prefix makes GUIDs greppable in logs and
distinguishable from agent session IDs. This GUID:

1. Is exported as `TICKET_SHEPHERD_HANDSHAKE_GUID` env var when the TMUX session is spawned
2. Is included in the bootstrap message (delivered as the initial prompt argument on agent
   start) so it's recorded in agent session artifacts for `AgentTypeAdapter.resolveSessionId()` resolution
3. Is included by the callback scripts in **every** callback to the server
4. Is stored in the in-memory `CurrentState` (ref.ap.K3vNzHqR8wYm5pJdL2fXa.E) alongside the agent's session ID (flushed to `current_state.json`)

```kotlin
// HandshakeGuid value class — ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E
// Generation — always use the factory to enforce the prefix
val guid = HandshakeGuid.generate()
```

### How the Callback Scripts Know the GUID

The TMUX session command exports the GUID before starting the agent:

```bash
export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.a1b2c3d4-... && export TICKET_SHEPHERD_SERVER_PORT=8347 \
  && claude --system-prompt-file <path> [flags] "<bootstrap_message>"
```

The callback scripts read `$TICKET_SHEPHERD_HANDSHAKE_GUID` from the environment and include
it in every HTTP callback. The agent itself does not need to know about
the GUID — it is transparent, handled entirely by the callback scripts.

**Fail-fast requirement:** Every callback script must hard-fail when `$TICKET_SHEPHERD_HANDSHAKE_GUID` is not
set. This catches misconfigured spawns immediately.

---

## Server Port — Environment Variable

The server port is configured via the **`TICKET_SHEPHERD_SERVER_PORT`** environment variable.
The harness reads this on startup and binds the Ktor CIO server to the specified port. If the
port is already in use, the harness **fails hard** with a clear error directing the user to
stop the other instance.

- Callback scripts read `$TICKET_SHEPHERD_SERVER_PORT` from the environment to construct the server URL
- The env var is exported in the TMUX session command alongside `TICKET_SHEPHERD_HANDSHAKE_GUID`
- **No temporary files** for port discovery — simple, explicit, and eliminates cleanup concerns

The server starts once at harness startup and stays alive across all sub-parts.

---

## V1 Server Endpoints

### Signal Endpoints (fire-and-forget — bare `200 OK`)

| Endpoint | Purpose |
|---|---|
| `POST /callback-shepherd/signal/started` | Bootstrap handshake — agent acknowledges it is alive and can reach the server. Harness sends full instructions only after receiving this. See ref.ap.xVsVi2TgoOJ2eubmoABIC.E. |
| `POST /callback-shepherd/signal/done` | Agent signals task completion with a `result` field. Result values are role-scoped (see Result Validation). |
| `POST /callback-shepherd/signal/user-question` | Agent has a question for the human. Server returns 200 immediately, appends question to `SessionEntry.questionQueue`. The executor's health-aware await loop detects the non-empty queue, collects answers via `UserQuestionHandler`, and batch-delivers all answers via TMUX `send-keys`. Health pings and noActivityTimeout suppressed while `isQAPending` is true (derived: `questionQueue.isNotEmpty()`). |
| `POST /callback-shepherd/signal/fail-workflow` | Unrecoverable error — aborts the entire workflow. Harness prints red error, kills all sessions, and exits non-zero (`FailedToExecutePlanUseCase`). |
| `POST /callback-shepherd/signal/self-compacted` | Agent signals it has completed context window self-compaction — wrote `PRIVATE.md`, ready for session rotation (ref.ap.8nwz2AHf503xwq8fKuLcl.E). |
| `POST /callback-shepherd/signal/ack-payload` | Agent acknowledges receipt of a `send-keys` payload — including health pings (see [Payload Delivery ACK Protocol](#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe) — ref.ap.r0us6iYsIRzrqHA5MVO0Q.E). Side-channel signal — clears `pendingPayloadAck` on `SessionEntry`, does not complete `signalDeferred`. |

---

## Agent Callback Contract + Request Payloads

All agent-to-server HTTP requests include `handshakeGuid` as the identity field.
No `branch` field — the server derives branch and sub-part context from its internal
GUID-to-sub-part registry.

### Signal Request Payloads

```json
// POST /callback-shepherd/signal/started — bootstrap handshake (agent alive, env correct, server reachable)
{ "handshakeGuid": "handshake.a1b2c3d4-..." }

// POST /callback-shepherd/signal/done — doer completing work
{ "handshakeGuid": "handshake.a1b2c3d4-...", "result": "completed" }

// POST /callback-shepherd/signal/done — reviewer: work passes review
{ "handshakeGuid": "handshake.a1b2c3d4-...", "result": "pass" }

// POST /callback-shepherd/signal/done — reviewer: work needs iteration
{ "handshakeGuid": "handshake.a1b2c3d4-...", "result": "needs_iteration" }

// POST /callback-shepherd/signal/user-question
{ "handshakeGuid": "handshake.a1b2c3d4-...", "question": "How should I handle X?" }

// POST /callback-shepherd/signal/fail-workflow
{ "handshakeGuid": "handshake.a1b2c3d4-...", "reason": "Cannot compile after multiple approaches" }

// POST /callback-shepherd/signal/ack-payload — acknowledges receipt of a send-keys payload (including health pings)
{ "handshakeGuid": "handshake.a1b2c3d4-...", "payloadId": "a1b2c3d4-3" }
```

### Result Validation on `/callback-shepherd/signal/done`

The `result` field is **required** on every `/callback-shepherd/signal/done` request. The server
looks up the sub-part role via HandshakeGuid → `SessionsState` and validates the value:

| Sub-Part Role | Valid `result` Values | Invalid → 400 |
|---|---|---|
| **Doer** (first sub-part) | `completed` | `pass`, `needs_iteration`, or any other value |
| **Reviewer** (second sub-part) | `pass`, `needs_iteration` | `completed` or any other value |

- Missing `result` field → 400 error
- Role-mismatched value → 400 error

This is strict validation — catches misconfigured agents immediately.

### Harness Reaction by Result

| Result | Who | Harness Action |
|--------|-----|---------------|
| `completed` | Doer | Move to reviewer sub-part (doer stays `IN_PROGRESS`); or complete part if no reviewer (doer → `COMPLETED`) |
| `pass` | Reviewer | Part complete → move to next part |
| `needs_iteration` | Reviewer | Check iteration counter: within budget → send new instructions to doer via TMUX `send-keys`, doer works, then on doer completion send new instructions to reviewer. Exceeds `iteration.max` → `FailedToConvergeUseCase` |

---

## Server-Side Routing

On callback arrival, server looks up HandshakeGuid in `SessionsState`
(ref.ap.7V6upjt21tOoCFXA7nqNh.E), validates result against sub-part role, and completes
the `signalDeferred` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E). See
[SessionsState](SessionsState.md) for the full bridge design.

### Routing

Signal endpoints (`/callback-shepherd/signal/*`): Look up `SessionEntry`, update
`lastActivityTimestamp`. Lifecycle signals (`done`, `fail-workflow`) additionally complete
`signalDeferred`. Side-channel signals (`started`, `user-question`, `ack-payload`)
do **not** complete the deferred — executor stays suspended. `user-question` additionally
appends the question to `SessionEntry.questionQueue`
(ref.ap.NE4puAzULta4xlOLh5kfD.E) — the executor's health-aware await loop picks it up.
`ack-payload` additionally clears `pendingPayloadAck`
on the `SessionEntry` when the PayloadId matches (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).

### Unknown HandshakeGuid

If `SessionsState.lookup(guid)` returns `null` (GUID not registered — stale, misconfigured,
or from a different harness instance), the server returns **404** and logs a **WARN** with
the unknown GUID. This is distinct from the "already completed" idempotent case (which
returns 200). Applies to both signal and query endpoints.

### Idempotent Signal Callbacks

When the server receives a lifecycle signal (`done` or `fail-workflow`) for a deferred that is
**already completed**, the behavior depends on **what signal arrives** — not just whether the
deferred is completed.

#### True Duplicates (same signal repeated, or `done` after `fail-workflow`)

If the server receives a `/callback-shepherd/signal/done` for a deferred already completed
with `Done`, or a `/callback-shepherd/signal/fail-workflow` for a deferred already completed
with `FailWorkflow`, or a `/callback-shepherd/signal/done` for a deferred already completed
with `FailWorkflow` — these are true duplicates or harmless late arrivals. The server:

1. Returns **200** (fire-and-forget principle)
2. Logs a **WARN** with the HandshakeGuid, received signal, and the fact that the deferred
   was already completed

**This server-side idempotency enables safe client-side retry** — callback scripts can retry
on transient failures (ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E) without risk of double-completing a
deferred or corrupting state. If the server processed the first request but the response was
lost (causing a retry), the duplicate request simply returns 200 and logs a warning.

#### Late `fail-workflow` After `done` — Log-Only ERROR (KISS Simplification)

If the server receives `/callback-shepherd/signal/fail-workflow` for a deferred already
completed with `Done`, the server treats it as a **log-only ERROR** — same handling pattern
as true duplicates, but at ERROR level instead of WARN.

The server:

1. Returns **200** (fire-and-forget — the agent has done its part by signaling)
2. Logs an **ERROR** (not WARN) with the HandshakeGuid, the late `fail-workflow` reason,
   and the fact that the deferred was previously completed with `Done`

**No halt propagation.** The `done` result stands. The reviewer step already validates the
doer's output — if the output is broken, the reviewer will catch it and signal
`needs_iteration`. This eliminates mutable `lateFailWorkflow` state on `SessionEntry`,
checkpoint logic at every transition point in `PartExecutor` and `TicketShepherd`, and the
multi-step halt propagation path — all for a rare edge case the reviewer already covers.

All WARN and above log messages are output to the **console** (via `OutFactory` `outConsumers`)
in addition to structured logs, so operators see duplicate callbacks and late `fail-workflow`
signals immediately.

---


## Agent Startup Acknowledgment / ap.xVsVi2TgoOJ2eubmoABIC.E

**Problem:** There is a large observability gap between agent spawn and the first
`/callback-shepherd/signal/done` call. If the agent fails to start (bad env, agent binary crash,
TMUX session issue, malformed instructions), the harness won't know until `healthTimeouts.normalActivity`
fires — 30 minutes later.

**Solution:** `/callback-shepherd/signal/started` — a lightweight signal endpoint that is
part of the **bootstrap handshake**. The agent receives a minimal bootstrap message as its
initial prompt (included in the TMUX start command) containing its HandshakeGuid and a single
instruction to call `callback_shepherd.signal.sh started`. Full work instructions are only sent
**after** the harness receives `/signal/started`.

### Contract

- The agent is spawned in **interactive mode** (no `-p`/`--print` flags). The bootstrap
  message is delivered as an **initial prompt argument** in the CLI command that starts the
  agent — no separate `send-keys` step. It is a lightweight, self-contained string — no
  instruction file needed.
- **Same handshake for new agents and resumed agents** — the only difference is the TMUX
  start command (`claude --system-prompt-file <path> [flags] "<bootstrap>"` vs
  `claude --resume <id> "<bootstrap>"`). Both embed the bootstrap as initial prompt. This
  makes the protocol universal and robust.
- Agent calls `callback_shepherd.signal.sh started` as its **first and only action** from the
  bootstrap message
- Payload: `{ "handshakeGuid": "handshake.xxx" }`
- Server: updates `lastActivityTimestamp` on the `SessionEntry` (same as any side-channel
  signal — no new machinery). Returns 200.
- **Does NOT flow through `AgentSignal`** — this is a side-channel signal, same as
  `user-question` and `ack-payload`.
- **On `/signal/started` received**: harness resolves agent session ID via `AgentTypeAdapter.resolveSessionId()`
  (GUID is now guaranteed in JSONL), then sends full instructions via TMUX `send-keys`

### Two-Phase Flow

```
Phase 1: Bootstrap Handshake (initial prompt argument — interactive agent, no -p)
  Harness ──[TMUX start: AgentTypeAdapter.buildStartCommand(bootstrap) — bootstrap
             is the initial prompt argument, delivered atomically on start]──► Agent
  Agent   ──[POST /signal/started]──────────────────────────────────────────► Server
  Harness ──[AgentTypeAdapter.resolveSessionId(): resolves session ID (GUID guaranteed)]

Phase 2: Work (TMUX send-keys — file pointer, only after /signal/started)
  Harness ──[send-keys: wrapped payload with PayloadId (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E)]──► Agent
  Agent   ──[POST /signal/ack-payload {payloadId}]──────────────────────────────────────► Server
  Agent   ──[works, calls /signal/done]─────────────────────────────────────────────────► Server
```

**Claude Code example** (the actual command is built by the agent-specific `AgentTypeAdapter` implementation):
```bash
# New agent — interactive start with bootstrap as initial prompt (no -p):
claude --system-prompt-file <path> [flags] "<bootstrap_message>"
# Resumed agent — interactive resume with bootstrap as initial prompt (no -p):
claude --resume <id> "<bootstrap_message>"
```

See [`AgentTypeAdapter` — Unified Interface](../use-case/SpawnTmuxAgentSessionUseCase.md#agenttypeadapter--unified-interface-for-agent-type-specific-behavior)
for the abstraction design. Full spawn flow: see [SpawnTmuxAgentSessionUseCase](../use-case/SpawnTmuxAgentSessionUseCase.md)
(ref.ap.hZdTRho3gQwgIXxoUtTqy.E).

### Startup Timeout

The facade uses the **`healthTimeouts.startup`** window (default: **3 minutes**) between
spawn and first callback. This is significantly shorter than the steady-state
`healthTimeouts.normalActivity` (30 min) because startup failures should be caught fast.

If no callback of any kind arrives within `healthTimeouts.startup` after spawn, the facade
triggers `AgentUnresponsiveUseCase` (`STARTUP_TIMEOUT`) → logs a clear error identifying the spawn failure → returns
`PartResult.AgentCrashed`. See [Health Monitoring](../use-case/HealthMonitoring.md)
(ref.ap.RJWVLgUGjO5zAwupNLhA0.E).

### Why This Works

- Validates the **entire communication chain** (agent CLI → env vars → HTTP → server
  routing) before any real work begins
- **No wasted work**: instruction assembly is deferred until agent is confirmed alive
- **No fixed startup delay**: the bootstrap is the agent's initial prompt argument —
  delivered atomically on start. The harness waits for the callback, not a timer.
- **No timing guesswork**: unlike a separate `send-keys` step, the initial prompt argument
  is guaranteed to be available when the agent starts processing. When `/started` arrives,
  the agent has proven it can process input — subsequent `send-keys` (Phase 2) are safe.
- **Simpler session ID resolution**: `AgentTypeAdapter.resolveSessionId()` runs after `/started`, when
  the GUID is guaranteed in the JSONL — no race condition, no polling timeout risk
- **Universal**: same handshake for new and resumed agents — one protocol to test and debug
- If env vars are misconfigured, the `/started` call either fails or never fires —
  both caught by the 3-minute timeout

After the first callback arrives, the facade switches to the `healthTimeouts.normalActivity`
window (30 min) for the remainder of the agent's work.

### WHY-NOT: Eliminating Phase 1 (Merging `/started` Into First `done`)

A natural simplification is to drop Phase 1 entirely — the first `/signal/done` would serve
as proof-of-life. This was evaluated and rejected for a fundamental reason:

**`/started` fires in seconds; the first `done` can take 30+ minutes.**

Phase 1 (`/signal/started`) fires immediately on agent startup — a trivial "I'm alive and
can reach the server" signal with no work attached. Phase 2 (`/signal/done`) only completes
when the agent finishes its work, which can take 30 minutes or more for complex tasks.

The two phases give the harness **precise state observability** at all times:

| State | What the harness knows | Timeout applied |
|-------|------------------------|-----------------|
| After spawn, before `/started` | Agent may not have started — startup may have failed | `noStartupAckTimeout` (3 min) |
| After `/started`, before `done` | Agent is alive and working | `noActivityTimeout` (30 min) |

Without Phase 1 (single-phase model):
- The harness cannot distinguish "agent crashed on startup" from "agent is working on a long
  task" — both look identical until the first callback arrives.
- To avoid false positives on long-running work, `noStartupAckTimeout` would have to be set
  to 30+ minutes — which is the same as having no startup timeout at all.
- A misconfigured or crashed agent would not be detected for 30+ minutes rather than 3.

The dual-timeout model (`noStartupAckTimeout` vs. `noActivityTimeout`) exists precisely
because these two windows have fundamentally different failure probabilities and acceptable
detection latencies. Merging the phases collapses this distinction.

---

## Structured Text Delivery — Instruction Files in `.ai_out/`

All structured/formatted content sent to agents is written to the sub-part's
`comm/in/instructions.md` file inside `.ai_out/`:

- Write content to `.ai_out/${branch}/.../${sub_part}/comm/in/instructions.md`
- Send file path to agent via TMUX `send-keys`: `"Read instructions at <path>"`
- Instructions are **overwritten** each iteration — git history preserves prior versions
- **Exception**: Simple single-line messages (e.g., GUID handshake for session ID resolution) can be sent directly

This replaces the previous temp file pattern. Instructions are now git-tracked alongside
agent outputs (`comm/out/PUBLIC.md`), providing full communication visibility.
See [`.ai_out/` directory schema](../schema/ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

---

## User-Question — Executor-Driven Q&A with Ping Suppression
<!-- ap.DvfGxWtvI1p6pDRXdHI2W.E — alias retained for backward refs -->

Agent calls `callback_shepherd.signal.sh user-question`. Server returns **200 immediately**
(fire-and-forget) and appends the question to `SessionEntry.questionQueue`
(ref.ap.igClEuLMC0bn7mDrK41jQ.E). The executor's health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E) detects the non-empty queue on its next tick, drains
it, collects answers via `UserQuestionHandler` strategy
(ref.ap.NE4puAzULta4xlOLh5kfD.E), and batch-delivers all answers via `AckedPayloadSender`
(ref.ap.tbtBcVN2iCl1xfHJthllP.E) — wrapped in Payload Delivery ACK XML, not via HTTP response.

While Q&A is pending (`isQAPending == true`, derived from `questionQueue.isNotEmpty()`),
the executor's health-aware await loop **suppresses** health pings and noActivityTimeout —
the agent is known-idle, awaiting a TMUX answer.

See [UserQuestionHandler](UserQuestionHandler.md) (ref.ap.NE4puAzULta4xlOLh5kfD.E) for the
executor-driven Q&A lifecycle, queuing model, batch delivery format, and strategy interface.

---

## Callback Scripts

`callback_shepherd.signal.sh` is the sole mechanism agents use to communicate back to the
harness.

**Behavior:**

- Lives on `$PATH` of the started agent
- Reads port from `$TICKET_SHEPHERD_SERVER_PORT` environment variable
- Reads `$TICKET_SHEPHERD_HANDSHAKE_GUID` from environment; includes it in every request
- **Fail-fast:** hard-fail when `$TICKET_SHEPHERD_HANDSHAKE_GUID` or `$TICKET_SHEPHERD_SERVER_PORT` is not set
- Expects 200 from server; **retries on transient failures** before exiting non-zero (see [Retry on Transient Failures](#retry-on-transient-failures--apyzc3q5teh2eycn03j7zule) below)
- Agent receives usage instructions in its initial instructions, wrapped in
  `<critical_to_keep_through_compaction>` tags to survive context compaction

### `callback_shepherd.signal.sh` — Fire-and-Forget

Posts to `/callback-shepherd/signal/<action>`. The HTTP response body is ignored — the
script only checks for 200 status. Any harness-to-agent content comes via TMUX `send-keys`.

```bash
callback_shepherd.signal.sh started                       # no extra args — bootstrap handshake
callback_shepherd.signal.sh done <result>                 # required: completed | pass | needs_iteration
callback_shepherd.signal.sh user-question "<text>"        # required: question text
callback_shepherd.signal.sh fail-workflow "<reason>"      # required: failure reason (aborts entire workflow)
callback_shepherd.signal.sh ack-payload <payload-id>      # required: PayloadId from the send-keys wrapper (e.g., "a1b2c3d4-3")
callback_shepherd.signal.sh self-compacted                # no extra args — context window self-compaction complete (ref.ap.8nwz2AHf503xwq8fKuLcl.E)
```

**Argument validation on `done`:** The script rejects anything other than `completed`,
`pass`, or `needs_iteration` with a clear error and non-zero exit. Server-side role
validation is a second layer of defense.

**Argument validation on `ack-payload`:** The script requires exactly one argument (the
PayloadId). Missing or empty PayloadId → error and non-zero exit.

### Retry on Transient Failures / ap.yzc3Q5TEh2EYCN03J7ZuL.E

`callback_shepherd.signal.sh` **retries on transient failures** before exiting non-zero. This prevents the most common class of silent
deadlock — a single server GC pause, momentary TCP reset, or connection blip causing a lost
signal that the agent will never re-send.

**Retry policy:**

| Parameter | Value |
|-----------|-------|
| Max retries | 2 (3 total attempts) |
| Backoff after 1st failure | 1 second |
| Backoff after 2nd failure | 5 seconds |
| After all retries exhausted | Exit non-zero (same as no-retry behavior) |

**What is transient** (retry applies):

| Failure | Cause |
|---------|-------|
| Connection refused | Server momentarily unreachable (startup race, restart) |
| Connection reset / TCP error | Network-level interruption mid-request |
| Timeout (curl timeout) | Server GC pause, slow response |
| HTTP 5xx | Server-side error (temporary) |

**What is NOT transient** (no retry — fail immediately):

| Failure | Cause |
|---------|-------|
| HTTP 400 | Bad request — client error, retrying won't fix it |
| HTTP 404 | Unknown HandshakeGuid — retrying won't fix it |
| Missing env vars | Configuration error — fail-fast before any HTTP call |

**Why retry is safe:**

Signal endpoints are inherently idempotent. Retry only fires when the prior attempt
failed (no 200 received), meaning the server likely never processed the request. Even if the
server did process it but the response was lost, the server handles duplicate signal callbacks
gracefully (see [Idempotent Signal Callbacks](#idempotent-signal-callbacks) — returns 200,
logs WARN).

**The deadlock this prevents:**

Without retry, a lost `/signal/done` creates an unrecoverable deadlock:
1. Agent calls `done` → transient HTTP failure → script exits non-zero
2. Agent believes it signaled completion (or treats the failure as non-fatal)
3. Harness never completes `signalDeferred` → waits indefinitely
4. Health monitor pings (after 30 min) → agent responds with `ack-payload` (it IS alive)
5. Loop repeats: harness pings → agent acks → harness waits → harness pings

The agent has no reason to re-send `done` — it already did. With retry, the transient failure
is resolved locally in the script before the agent ever sees an error.

The same applies to `/signal/started` — without retry, a transient failure during the
bootstrap handshake causes `AgentUnresponsiveUseCase` (`STARTUP_TIMEOUT`) to kill the agent after 3 minutes, wasting a
healthy agent over a momentary HTTP blip.

---

## Harness → Agent Communication

Harness-to-agent communication uses two channels depending on the phase:

- **Bootstrap** (Phase 1): delivered as an **initial prompt argument** in the CLI command
  that starts the agent. The bootstrap message containing HandshakeGuid and
  `callback_shepherd.signal.sh started` instruction is embedded in the start command itself
  (both new and `--resume`). Lightweight, self-contained string — no instruction file. No
  separate `send-keys` step — the agent receives the bootstrap atomically on startup.
- **Work** (Phase 2 — after `/signal/started` received): TMUX **`send-keys`** is the channel
  for all post-bootstrap communication. Agents are spawned in **interactive mode** (no
  `-p`/`--print` flags) — the entire point of TMUX is to allow ongoing communication with a
  running agent:
  - Send instruction file paths (`comm/in/instructions.md` in `.ai_out/`)
  - Send user-question answer file paths (`comm/in/qa_answers.md` in `.ai_out/`)
  - Send health pings
  - Send iteration feedback instructions (on `needs_iteration`)

**All `send-keys` payloads are wrapped with the Payload Delivery ACK
Protocol** (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) — the agent must ACK receipt before processing.
This includes health pings, which use the same generic ACK mechanism as all other payloads.

**Input corruption prevention:** All content delivery uses `TmuxCommunicator.sendKeys()`
(ref.ap.4cY9sc1jEQEseLgR7nDq0.E) which sends text via TMUX `send-keys -l` (literal flag) —
preventing TMUX from interpreting payload content as key names (e.g., "Space", "Enter",
"Escape"). A separate `sendRawKeys()` method sends without `-l` for control sequences —
reserved for V2 emergency compaction (see
[`doc_v2/our-own-emergency-compression.md`](../../doc_v2/our-own-emergency-compression.md)).
V1 uses only `sendKeys()` (literal mode). See `TmuxCommunicatorImpl`
(ref.ap.3BCYPiR792a2B8I9ZONDwmvN.E) for the implementation.

For the full agent lifecycle (spawn, session ID resolution), see
[`SpawnTmuxAgentSessionUseCase`](../use-case/SpawnTmuxAgentSessionUseCase.md) (ref.ap.hZdTRho3gQwgIXxoUtTqy.E).

---

## Payload Delivery ACK Protocol / ap.r0us6iYsIRzrqHA5MVO0Q.E

### Problem

TMUX `send-keys` succeeds if the TMUX session exists — regardless of whether the agent
actually processes the input. If the agent's context is compacting, or the agent is
mid-tool-execution, or the pane buffer is full, the content may arrive but never be
processed. Without delivery confirmation, the harness cannot distinguish "agent is working
on the instruction" from "agent never received the instruction."

This creates a specific failure mode: instruction delivered via `send-keys` → agent never
processes it → agent stays idle → `healthTimeouts.normalActivity` (30 min) → health ping → agent responds
with `ack-payload` (it IS alive, just never got the instruction) → timeout resets → repeat
indefinitely. Health monitoring cannot break this loop because the agent is demonstrably
alive.

### Solution

Every `send-keys` payload is wrapped in XML with a unique **PayloadId**
(ref.ap.GWfkDyTJbMpWhPSPnQHlO.E). The agent must ACK the payload by calling
`callback_shepherd.signal.sh ack-payload <PAYLOAD_ID>` **before** proceeding with the
payload content. If no ACK arrives within the timeout window, the harness retries delivery.

### PayloadId / ap.GWfkDyTJbMpWhPSPnQHlO.E

Format: `{handshakeGuid_short}-{sequenceN}` — e.g., `a1b2c3d4-3` for the 3rd payload in
that session. The `handshakeGuid_short` is the first 8 characters of the session's
HandshakeGuid UUID (excluding the `handshake.` prefix). `sequenceN` is a per-session
`AtomicInteger` counter starting at 1, incremented for each payload sent.

**Why simpler:** Counter increment vs. random string generation — no UUID/random library
dependency for PayloadId generation.

**Why more observable:** The ID is self-correlating — seeing `a1b2c3d4-3` in logs
immediately identifies the session (via the 8-char prefix) and payload sequence within that
session. Sequence numbers reveal gaps: if payload 2 is ACKed but 3 is not, the problem is
immediately clear. Deterministic IDs enable easier test assertions.

```kotlin
// PayloadId value class — wraps {handshakeGuid_short}-{sequenceN}
// Generation — uses an AtomicInteger counter per session, starting at 1
val payloadId = PayloadId.generate(handshakeGuid, counter)
```

### Wrapping Format

```xml
<payload_from_shepherd_must_ack payload_id="a1b2c3d4-3" MUST_ACK_BEFORE_PROCEEDING="callback_shepherd.signal.sh ack-payload a1b2c3d4-3">
Read instructions at /path/to/comm/in/instructions.md
</payload_from_shepherd_must_ack>
```

**Design choices:**
- The XML tag name (`payload_from_shepherd_must_ack`) is deliberately verbose — it serves as
  an inline instruction to the agent, readable even without prior context
- The `MUST_ACK_BEFORE_PROCEEDING` attribute contains the **exact command** the agent must
  run — the agent does not need to construct the command itself
- The `payload_id` attribute is redundant with the command (for greppability and debugging)

### Scope — What Gets Wrapped

| Content type | Wrapped? | Rationale |
|---|---|---|
| Work instructions (Phase 2 file pointer) | **Yes** | Core delivery — must confirm receipt |
| User-question answers (ref.ap.NE4puAzULta4xlOLh5kfD.E) | **Yes** | Agent is waiting for this; non-delivery blocks progress |
| Iteration feedback instructions | **Yes** | Critical for doer re-instruction after `needs_iteration` |
| PUBLIC.md re-instruction (ref.ap.THDW9SHzs1x2JN9YP9OYU.E) | **Yes** | Agent must produce PUBLIC.md before part can proceed |
| Health pings | **Yes** | Pings use the same generic ACK mechanism as all other payloads — one fewer endpoint, one fewer code path (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) |

**Bootstrap messages are NOT wrapped** — they are delivered as initial prompt arguments
(not `send-keys`), and the `/signal/started` callback serves as their ACK
(ref.ap.xVsVi2TgoOJ2eubmoABIC.E).

### Shared Abstraction — AckedPayloadSender / ap.tbtBcVN2iCl1xfHJthllP.E

All callers that deliver payloads to agents via `send-keys` (every "Yes" row in the scope
table above) **must** use a single shared abstraction: `AckedPayloadSender`. This is the
sole gateway for **all** harness→agent `send-keys` communication — including health pings.

```kotlin
interface AckedPayloadSender {
    /**
     * Wrap [payloadContent] in the Payload Delivery ACK XML, send via TMUX send-keys,
     * and await ACK. Retries per the retry policy (3 attempts, 3 min each).
     *
     * Returns on successful ACK. Throws on all-retries-exhausted (caller handles as crash).
     */
    suspend fun sendAndAwaitAck(
        tmuxSession: TmuxAgentSession,
        sessionEntry: SessionEntry,
        payloadContent: String,
    )
}
```

**Why a shared abstraction:**

- **DRY**: The wrap → send-keys → ACK-await → retry loop is identical across all use cases
  (work instructions, Q&A answers, iteration feedback, PUBLIC.md re-instruction, health pings).
  Duplicating this logic creates divergence risk.
- **Single place for retry policy**: Timeout, retry count, and failure behavior are defined
  once. Callers don't re-implement the retry loop.
- **Testability**: Each caller tests its own orchestration; `AckedPayloadSender` tests the
  delivery mechanism. Clear boundary.

**Callers:**

| Caller | When |
|--------|------|
| `AgentFacadeImpl` health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) | Phase 2 work instructions after bootstrap |
| `AgentFacadeImpl` via `sendPayloadAndAwaitSignal` — triggered by PartExecutor re-instruction (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) | Iteration feedback to doer/reviewer |
| Executor Q&A answer batch delivery (ref.ap.NE4puAzULta4xlOLh5kfD.E) | After executor drains `questionQueue` and collects all answers via `UserQuestionHandler` |
| `AgentFacadeImpl` health-aware await loop — health ping (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) | When `lastActivityTimestamp` stale > `healthTimeouts.normalActivity` |

### ACK Flow

```
AgentFacadeImpl                         Agent (in TMUX)                    Server
   │                                         │                               │
   ├─ generate PayloadId                     │                               │
   ├─ wrap payload in XML                    │                               │
   ├─ send-keys (wrapped payload) ──────────►│                               │
   ├─ start ACK-await (3 min timeout)        │                               │
   │                                         ├─ reads XML wrapper            │
   │                                         ├─ callback_shepherd.signal.sh  │
   │                                         │  ack-payload <PayloadId> ────►│
   │                                         │                               ├─ records ACK
   │                                         │                               │  on SessionEntry
   │  ◄── ACK confirmed (via SessionEntry) ──┤                               │
   │                                         │                               │
   ├─ proceed: enter health-aware            │                               │
   │  await loop                             ├─ processes payload content    │
   │                                         │  (reads instructions, works)  │
```

**On ACK received:** the facade knows the agent has received and is processing the
payload. It proceeds to the normal health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E) waiting for `done`/`fail-workflow`.

**On ACK timeout (3 min):** the harness retries `send-keys` with the **same PayloadId**
and the same wrapped content. The content is unchanged — the instruction file path hasn't
moved. This is idempotent from the agent's perspective: if the agent somehow receives the
payload twice, it reads the same instruction file.

### Retry Policy

| Attempt | Action | On no ACK |
|---------|--------|-----------|
| 1 (initial send) | Send wrapped payload via `send-keys` | Wait 3 minutes |
| 2 (first retry) | Re-send same wrapped payload via `send-keys` | Wait 3 minutes |
| 3 (second retry) | Re-send same wrapped payload via `send-keys` | All retries exhausted |

**After all retries exhausted** (no ACK after 9 minutes total): the facade treats this as
an agent that is alive but unable to process input — functionally equivalent to a crash.
The facade kills the TMUX session, completes `signalDeferred` with `AgentSignal.Crashed`,
and returns `PartResult.AgentCrashed`. `TicketShepherd` delegates to
`FailedToExecutePlanUseCase` (red error, kills all sessions, exits non-zero).

### ACK Tracking on SessionEntry

The `SessionEntry` (ref.ap.igClEuLMC0bn7mDrK41jQ.E) carries an optional
`pendingPayloadAck: PayloadId?` field. The facade sets this before sending `send-keys`.
The server sets it to `null` when the matching `/signal/ack-payload` arrives. The facade
polls this field during the ACK-await phase.

### Signal Endpoint

`POST /callback-shepherd/signal/ack-payload`

Side-channel signal — same tier as `started`, `user-question`:
- Updates `lastActivityTimestamp`
- Clears `pendingPayloadAck` on `SessionEntry` when `payloadId` matches
- Does **NOT** complete `signalDeferred` — the facade is in the ACK-await phase, not the
  signal-await phase
- Returns bare `200 OK`

**Mismatched PayloadId:** If the `payloadId` in the request does not match the
`pendingPayloadAck` on the `SessionEntry` (stale ACK from a previous retry), the server
returns `200` (fire-and-forget principle), logs a **WARN** with both IDs, and does **NOT**
clear `pendingPayloadAck`. The timestamp is still updated.

### Duplicate ACK Handling

If `pendingPayloadAck` is already `null` when an `/ack-payload` arrives (agent ACKed
twice, or callback script retry after the first attempt succeeded), the server returns
`200` and logs a **WARN**. Same idempotency principle as duplicate `/signal/done`
(see [Idempotent Signal Callbacks](#idempotent-signal-callbacks)).

### Why Self-Describing Wrapper

The wrapper contains the exact ACK command — the agent does not need prior knowledge of
the ACK protocol. Even if the agent's context window has compacted and lost earlier
instructions, the XML wrapper tells it exactly what to do: run
`callback_shepherd.signal.sh ack-payload <ID>` before proceeding. This makes the protocol
robust against context window pressure.

The agent's initial instructions (in `<critical_to_keep_through_compaction>` tags) also
explain the ACK protocol as background context. But the wrapper is the primary mechanism —
the agent should be able to ACK correctly from the wrapper alone.

### Relationship to Other Acknowledgment Mechanisms

| Mechanism | Scope | Direction |
|---|---|---|
| `/signal/started` (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Bootstrap message (Phase 1) | Agent → Harness |
| `/signal/ack-payload` (this section) | All `send-keys` payloads including health pings (Phase 2) | Agent → Harness |

These two mechanisms together ensure **every** message from harness to agent has a
confirmation path. The bootstrap has `/started`. Everything else (work instructions,
Q&A answers, iteration feedback, health pings) has `/ack-payload`.
