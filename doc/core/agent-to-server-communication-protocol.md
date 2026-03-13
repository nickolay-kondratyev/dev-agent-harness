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
│             │  {signal|query}.sh    │                    │
│             │ ←──────────────────   │                    │
│             │   TMUX send-keys      │                    │
└────────────┘                        └──────────────────┘
```

Communication is **bidirectional** through two distinct channels:

- **Agent → Harness**: HTTP POST via two callback scripts (`callback_shepherd.signal.sh` and `callback_shepherd.query.sh`). Each script handles port discovery and GUID injection transparently.
- **Harness → Agent**: TMUX `send-keys` is the only channel from harness to agent. Used for delivering instructions, Q&A answers, and health pings.

---

## Two-Tier Endpoint Design

Agent-to-harness HTTP endpoints are split into **two explicit tiers** based on their response contract. This makes the communication pattern self-documenting — the tier name tells the agent (and the developer) whether to read the HTTP response.

### Tier 1 — Signals (`/callback-shepherd/signal/*`)

**Fire-and-forget.** The agent POSTs, gets a bare `200 OK`, and moves on. If the harness needs to deliver content back (Q&A answers, iteration feedback, pings), it uses TMUX `send-keys`. Agents that need a response after a signal (e.g., after `user-question`) must **wait** for it to arrive via TMUX, not via the HTTP response.

Signals either complete `CompletableDeferred<AgentSignal>` (lifecycle signals: `done`, `fail-workflow`) or update `lastActivityTimestamp` only (side-channel signals: `started`, `user-question`, `ping-ack`).

### Tier 2 — Queries (`/callback-shepherd/query/*`)

**Synchronous request/response.** The agent POSTs, reads the meaningful HTTP response body, and acts on it immediately. No TMUX involved, no deferred completion. Queries are utility calls — they do not affect agent lifecycle or flow through `AgentSignal`.

### Why Two Tiers

- **Agent authors can't accidentally block on a signal** or ignore a query response — the tier name in the URL and script makes the contract obvious
- **New endpoints have a clear home** — "does the agent need the response?" → query; otherwise → signal
- **Server implementation is cleanly separated** — signal handlers complete deferreds / update timestamps; query handlers return data. No mixed concerns

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
2. Is included in the bootstrap message (passed as inline CLI args, e.g., `-p "..."`) so
   it's recorded in agent session artifacts for `AgentSessionIdResolver` resolution
3. Is included by the callback scripts in **every** callback to the server
4. Is stored in `current_state.json` alongside the agent's session ID

```kotlin
// HandshakeGuid value class — ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E
// Generation — always use the factory to enforce the prefix
val guid = HandshakeGuid.generate()
```

### How the Callback Scripts Know the GUID

The TMUX session command exports the GUID before starting the agent:

```bash
export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.a1b2c3d4-... && export TICKET_SHEPHERD_SERVER_PORT=8347 && claude
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
| `POST /callback-shepherd/signal/user-question` | Agent has a question for the human. Server returns 200 immediately; answer delivered asynchronously via TMUX `send-keys`. |
| `POST /callback-shepherd/signal/fail-workflow` | Unrecoverable error — aborts the entire workflow. Harness prints red error and halts (`FailedToExecutePlanUseCase`). |
| `POST /callback-shepherd/signal/ping-ack` | Agent acknowledges a health ping (see Agent Health Monitoring in [high-level.md](../high-level.md)). |

### Query Endpoints (synchronous — meaningful response body)

| Endpoint | Purpose |
|---|---|
| `POST /callback-shepherd/query/validate-plan` | Validates a `plan.json` file against the parts/sub-parts schema (ref.ap.56azZbk7lAMll0D4Ot2G0.E). Returns validation result in the response body. Planning-phase only. |

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

// POST /callback-shepherd/signal/ping-ack
{ "handshakeGuid": "handshake.a1b2c3d4-..." }
```

### Query Request Payloads

```json
// POST /callback-shepherd/query/validate-plan — returns validation result in response body
{ "handshakeGuid": "handshake.a1b2c3d4-...", "planFilePath": "/abs/path/to/.ai_out/branch/harness_private/plan.json" }
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
| `completed` | Doer | Move to reviewer sub-part (or complete part if no reviewer) |
| `pass` | Reviewer | Part complete → move to next part |
| `needs_iteration` | Reviewer | Check iteration counter: within budget → send new instructions to doer via TMUX `send-keys`, doer works, then on doer completion send new instructions to reviewer. Exceeds `iteration.max` → `FailedToConvergeUseCase` |

---

## Server-Side Routing

On callback arrival, server looks up HandshakeGuid in `SessionsState`
(ref.ap.7V6upjt21tOoCFXA7nqNh.E), validates result against sub-part role, and completes
the `signalDeferred` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E). See
[SessionsState](SessionsState.md) for the full bridge design.

### Routing by Tier

- **Signal endpoints** (`/callback-shepherd/signal/*`): Look up `SessionEntry`, update
  `lastActivityTimestamp`. Lifecycle signals (`done`, `fail-workflow`) additionally complete
  `signalDeferred`. Side-channel signals (`started`, `user-question`, `ping-ack`) do **not**
  complete the deferred — executor stays suspended.
- **Query endpoints** (`/callback-shepherd/query/*`): Look up `SessionEntry`, update
  `lastActivityTimestamp`, process the request, return meaningful response body. Do **not**
  complete `signalDeferred` — queries are utility calls, not lifecycle events.

### Unknown HandshakeGuid

If `SessionsState.lookup(guid)` returns `null` (GUID not registered — stale, misconfigured,
or from a different harness instance), the server returns **404** and logs a **WARN** with
the unknown GUID. This is distinct from the "already completed" idempotent case (which
returns 200). Applies to both signal and query endpoints.

### Idempotent Signal Callbacks

If the server receives a `/callback-shepherd/signal/done` or `/callback-shepherd/signal/fail-workflow`
callback for a deferred that is **already completed** (e.g., agent calls `done` twice, or
`done` after `fail-workflow`), `CompletableDeferred.complete()` returns `false`. The server:

1. Returns **200** (fire-and-forget principle — agent must not retry on non-200)
2. Logs a **WARN** with the HandshakeGuid, received signal, and the fact that the deferred
   was already completed

All WARN and above log messages are output to the **console** (via `OutFactory` `outConsumers`)
in addition to structured logs, so operators see duplicate callbacks immediately.

---

## Plan Validation Query
<!-- ap.R8mNvKx3wQ5pLfYtJ7dZe.E -->

`/callback-shepherd/query/validate-plan` is a **Tier 2 (Query)** endpoint — it returns
meaningful content in the HTTP response body. The agent reads the response to determine
whether the plan is valid. This is a **synchronous utility call**, not a lifecycle signal —
it does not flow through `AgentSignal` or `CompletableDeferred`.

Always returns HTTP 200. The response body carries the validation result:

```json
// Validation passed
{ "valid": true }

// Validation failed — errors describe what's wrong
{ "valid": false, "errors": ["subParts[0] missing required field: agentType", "..."] }
```

The server reads the file at the **absolute path** provided by the agent (the agent sends
the full filesystem path). Parses it against the parts/sub-parts schema
(ref.ap.56azZbk7lAMll0D4Ot2G0.E) and returns structured validation results.

**Validation rules** (for `with-planning` workflows):
1. Valid JSON conforming to the parts/sub-parts schema
2. At least one execution part exists
3. At least one sub-part has `loadsPlan: true`
4. Every `agentType` value is a supported type (V1: `ClaudeCode`)
5. Every `model` value is valid for the given `agentType`
6. Every `role` value matches an existing `.md` file in `$TICKET_SHEPHERD_AGENTS_DIR`
   (ticket: `nid_m5e0q3oslsihsxsz1h6no6nwr_E` — role catalog design). Catches non-existent
   role assignments at plan validation time rather than failing late during execution when
   `ContextForAgentProvider` tries to load the role definition.

Both the **planner** and **plan reviewer** are instructed to call this before signaling
`done` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E).

---

## Agent Startup Acknowledgment / ap.xVsVi2TgoOJ2eubmoABIC.E

**Problem:** There is a large observability gap between agent spawn and the first
`/callback-shepherd/signal/done` call. If the agent fails to start (bad env, agent binary crash,
TMUX session issue, malformed instructions), the harness won't know until `noActivityTimeout`
fires — 30 minutes later.

**Solution:** `/callback-shepherd/signal/started` — a lightweight signal endpoint that is
part of the **bootstrap handshake**. The agent receives a minimal bootstrap message as its
initial prompt (included in the TMUX start command) containing its HandshakeGuid and a single
instruction to call `callback_shepherd.signal.sh started`. Full work instructions are only sent
**after** the harness receives `/signal/started`.

### Contract

- The bootstrap message is passed as **inline CLI arguments** (`-p "..."`), NOT via TMUX
  `send-keys`. It is a lightweight, self-contained string — no instruction file needed.
- **Same handshake for new agents and resumed agents** — the only difference is the CLI
  command (`claude --system-prompt-file <path> -p "..."` vs `claude --resume <id> -p "..."`).
  This makes the protocol universal and robust.
- Agent calls `callback_shepherd.signal.sh started` as its **first and only action** from the
  bootstrap message
- Payload: `{ "handshakeGuid": "handshake.xxx" }`
- Server: updates `lastActivityTimestamp` on the `SessionEntry` (same as any side-channel
  signal — no new machinery). Returns 200.
- **Does NOT flow through `AgentSignal`** — this is a side-channel signal, same as
  `ping-ack` and `user-question`.
- **On `/signal/started` received**: harness resolves agent session ID via `AgentSessionIdResolver`
  (GUID is now guaranteed in JSONL), then sends full instructions via TMUX `send-keys`

### Two-Phase Flow

```
Phase 1: Bootstrap Handshake (CLI args — inline, no file)
  Harness ──[TMUX start: AgentStarter.buildStartCommand() "<bootstrap>"]──► Agent
  Agent   ──[POST /signal/started]────────────────────────────────────────► Server
  Harness ──[AgentSessionIdResolver: resolves session ID (GUID guaranteed)]

Phase 2: Work (TMUX send-keys — file pointer, only after /signal/started)
  Harness ──[send-keys: "Read instructions at <path>"]──► Agent
  Agent   ──[works, calls /signal/done]─────────────────► Server
```

**Claude Code example** (the actual command is built by the agent-specific `AgentStarter` implementation):
```bash
# New agent:
claude --system-prompt-file <path> -p "<bootstrap>"
# Resumed agent:
claude --resume <id> -p "<bootstrap>"
```

See [`AgentStarter` — Interface for Start Command](../use-case/SpawnTmuxAgentSessionUseCase.md#agentstarter--interface-for-start-command)
for the abstraction design. Full spawn flow: see [SpawnTmuxAgentSessionUseCase](../use-case/SpawnTmuxAgentSessionUseCase.md)
(ref.ap.hZdTRho3gQwgIXxoUtTqy.E).

### Startup Timeout

The executor uses a dedicated **`noStartupAckTimeout`** (default: **3 minutes**) for the
window between spawn and first callback. This is significantly shorter than the general
`noActivityTimeout` (30 min) because startup failures should be caught fast.

If no callback of any kind arrives within `noStartupAckTimeout` after spawn, the executor
triggers `NoStartupAckUseCase` → logs a clear error identifying the spawn failure → returns
`PartResult.AgentCrashed`. See [Health Monitoring](../use-case/HealthMonitoring.md)
(ref.ap.RJWVLgUGjO5zAwupNLhA0.E).

### Why This Works

- Validates the **entire communication chain** (TMUX → agent → env vars → HTTP → server
  routing) before any real work begins
- **No wasted work**: instruction assembly is deferred until agent is confirmed alive
- **No fixed startup delay**: replaces the previous `agentStartupDelay` (5s) — the
  bootstrap is passed as CLI args, and the harness waits for the callback, not a timer
- **Simpler session ID resolution**: `AgentSessionIdResolver` runs after `/started`, when
  the GUID is guaranteed in the JSONL — no race condition, no polling timeout risk
- **Universal**: same handshake for new and resumed agents — one protocol to test and debug
- If env vars are misconfigured, the `/started` call either fails or never fires —
  both caught by the 3-minute timeout

After the first callback arrives, the executor switches to the normal `noActivityTimeout`
(30 min) for the remainder of the agent's work.

---

## Structured Text Delivery — Instruction Files in `.ai_out/`

All structured/formatted content sent to agents is written to the sub-part's
`comm/in/instructions.md` file inside `.ai_out/`:

- Write content to `.ai_out/${branch}/.../${sub_part}/comm/in/instructions.md`
- Send file path to agent via TMUX `send-keys`: `"Read instructions at <path>"`
- Instructions are **overwritten** each iteration — git history preserves prior versions
- **Exception**: Simple single-line messages (e.g., AgentSessionIdResolver GUID handshake) can be sent directly

This replaces the previous temp file pattern. Instructions are now git-tracked alongside
agent outputs (`comm/out/PUBLIC.md`), providing full communication visibility.
See [`.ai_out/` directory schema](../schema/ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

---

## User-Question — Handled via Strategy
<!-- ap.DvfGxWtvI1p6pDRXdHI2W.E — alias retained for backward refs -->

Agent calls `callback_shepherd.signal.sh user-question`. Server delegates to
`UserQuestionHandler` — see [UserQuestionHandler](UserQuestionHandler.md)
(ref.ap.NE4puAzULta4xlOLh5kfD.E) for the strategy interface, V1 stdin behavior, and flow.
Answer delivered via TMUX `send-keys`, not via HTTP response.

---

## Callback Scripts

Two scripts — one per tier — are the sole mechanism agents use to communicate back to the
harness. The tier split mirrors the endpoint design: signals are fire-and-forget, queries
return meaningful responses.

**Shared behavior across both scripts:**

- Lives on `$PATH` of the started agent
- Reads port from `$TICKET_SHEPHERD_SERVER_PORT` environment variable
- Reads `$TICKET_SHEPHERD_HANDSHAKE_GUID` from environment; includes it in every request
- **Fail-fast:** hard-fail when `$TICKET_SHEPHERD_HANDSHAKE_GUID` or `$TICKET_SHEPHERD_SERVER_PORT` is not set
- Expects 200 from server; exits non-zero on any other status code
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
callback_shepherd.signal.sh ping-ack                      # no extra args — acknowledges health ping
```

**Argument validation on `done`:** The script rejects anything other than `completed`,
`pass`, or `needs_iteration` with a clear error and non-zero exit. Server-side role
validation is a second layer of defense.

### `callback_shepherd.query.sh` — Synchronous Request/Response

Posts to `/callback-shepherd/query/<action>`. The HTTP response body is **meaningful** —
the script prints it to stdout for the agent to read and act on.

```bash
callback_shepherd.query.sh validate-plan <abs-path>       # required: absolute path to plan.json — prints validation result to stdout
```

**Contract:** Agents calling `callback_shepherd.query.sh` must read stdout. The response
is the whole point — unlike signals, the HTTP body carries the result.

---

## Harness → Agent Communication

TMUX is the **only** harness-to-agent communication channel, through two mechanisms:

- **CLI args** (bootstrap): delivers the bootstrap message containing HandshakeGuid and
  `callback_shepherd.signal.sh started` instruction. Passed inline as `-p "..."` at agent start
  (both new and `--resume`). Lightweight, self-contained string — no instruction file.
- **`send-keys`** (work phase): used for all communication **after** the bootstrap handshake
  (`/signal/started` received):
  - Send instruction file paths (`comm/in/instructions.md` in `.ai_out/`)
  - Send user-question answer file paths (`comm/in/` in `.ai_out/`)
  - Send health pings
  - Send iteration feedback instructions (on `needs_iteration`)

For the full agent lifecycle (spawn, session ID resolution), see
[`SpawnTmuxAgentSessionUseCase`](../use-case/SpawnTmuxAgentSessionUseCase.md) (ref.ap.hZdTRho3gQwgIXxoUtTqy.E).
