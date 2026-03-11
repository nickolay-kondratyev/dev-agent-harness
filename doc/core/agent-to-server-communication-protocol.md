# Agent-to-Server Communication Protocol / ap.wLpW8YbvqpRdxDplnN7Vh.E

Defines the full communication protocol between agents running in TMUX sessions and the Shepherd harness server — identity, discovery, endpoints, payloads, and CLI tooling.

---

## Vocabulary

| Term | Definition |
|------|------------|
| **ShepherdServer** (aka Server) | The long-lived HTTP server instance that starts at harness launch and handles all requests from agents. One per harness process. |
| **Agent** | An instance of a code agent (e.g., Claude Code, PI) running in a TMUX session. In the future, multiple agents may be alive simultaneously. |
| **HandshakeGuid** | A harness-generated identifier (`handshake.${UUID}`) assigned to each agent session. Used in all agent↔server communication. The agent receives it as an env var; the server uses it to route callbacks. |

---

## Architecture

```
┌────────────┐  HTTP POST (curl)   ┌──────────────────┐
│  Agent      │ ──────────────→    │  Harness Server   │
│  (in TMUX)  │  callback_shepherd │  (Ktor CIO)       │
│             │  *.sh scripts      │                    │
│             │ ←──────────────    │                    │
│             │   TMUX send-keys   │                    │
└────────────┘                     └──────────────────┘
```

Communication is **bidirectional** through two distinct channels:

- **Agent → Harness**: HTTP POST via `callback_shepherd.*.sh` scripts (wrap curl). Each script handles port discovery and GUID injection transparently.
- **Harness → Agent**: TMUX `send-keys` is the only channel from harness to agent. Used for delivering instructions, Q&A answers, and health pings.

---

## Core Principle: All HTTP Callbacks Are Non-Blocking

**Every `callback_shepherd.*.sh` call is fire-and-forget.** The script POSTs to the server, expects a `200` response, and returns immediately. The server **never** holds an HTTP connection open for a long-running operation.

When the harness needs to deliver content back to the agent (Q&A answers, iteration feedback, pings), it uses **TMUX `send-keys`** — the only harness→agent channel. Agents that need a response (e.g., after `user-question`) must **wait** for it to arrive via TMUX, not via the HTTP response.

This eliminates fragile long-lived HTTP connections and keeps the protocol simple: HTTP is always agent→harness signaling; TMUX is always harness→agent delivery.

---

## HandshakeGuid — Agent Identity Key

### Problem

The server needs to know **which agent** is calling on every callback (`/callback-shepherd/done`,
`/callback-shepherd/user-question`, etc.). In a single-agent world this is trivial, but the design must
be multi-agent ready — multiple agents alive at the same time (e.g., implementor and
reviewer talking to each other).

### Solution

Every agent session gets a **HandshakeGuid** — a harness-generated identifier with format
`handshake.${UUID}`. The `handshake.` prefix makes GUIDs greppable in logs and
distinguishable from agent session IDs. This GUID:

1. Is exported as `TICKET_SHEPHERD_HANDSHAKE_GUID` env var when the TMUX session is spawned
2. Is sent to the agent as the first TMUX message (for AgentSessionIdResolver resolution)
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
export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.a1b2c3d4-... && claude
```

The callback scripts read `$TICKET_SHEPHERD_HANDSHAKE_GUID` from the environment and include
it in every HTTP callback. The agent itself does not need to know about
the GUID — it is transparent, handled entirely by the callback scripts.

**Fail-fast requirement:** Every callback script must hard-fail when `$TICKET_SHEPHERD_HANDSHAKE_GUID` is not
set. This catches misconfigured spawns immediately.

---

## Server Port Discovery

The server binds to **port 0** (OS-assigned). On startup, it writes the assigned port to:

```
$HOME/.shepherd_agent_harness/server/port.txt
```

- Callback scripts read this file to construct the server URL
- On server shutdown, this file is **deleted**
- **No env var needed** — eliminates port collision risk entirely

The server starts once at harness startup and stays alive across all sub-parts.

---

## V1 Server Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /callback-shepherd/done` | Agent signals task completion with a `result` field. Result values are role-scoped (see Result Validation). |
| `POST /callback-shepherd/user-question` | Agent has a question for the human. Server returns 200 immediately; answer delivered asynchronously via TMUX `send-keys`. |
| `POST /callback-shepherd/fail-workflow` | Unrecoverable error — aborts the entire workflow. Triggers `FailedToExecutePlanUseCase`. |
| `POST /callback-shepherd/ping-ack` | Agent acknowledges a health ping (see Agent Health Monitoring in [high-level.md](../high-level.md)). |

---

## Agent Callback Contract + Request Payloads

All agent-to-server HTTP requests include `handshakeGuid` as the identity field.
No `branch` field — the server derives branch and sub-part context from its internal
GUID-to-sub-part registry.

### Request Payloads

```json
// POST /callback-shepherd/done — doer completing work
{ "handshakeGuid": "handshake.a1b2c3d4-...", "result": "completed" }

// POST /callback-shepherd/done — reviewer: work passes review
{ "handshakeGuid": "handshake.a1b2c3d4-...", "result": "pass" }

// POST /callback-shepherd/done — reviewer: work needs iteration
{ "handshakeGuid": "handshake.a1b2c3d4-...", "result": "needs_iteration" }

// POST /callback-shepherd/user-question
{ "handshakeGuid": "handshake.a1b2c3d4-...", "question": "How should I handle X?" }

// POST /callback-shepherd/fail-workflow
{ "handshakeGuid": "handshake.a1b2c3d4-...", "reason": "Cannot compile after multiple approaches" }

// POST /callback-shepherd/ping-ack
{ "handshakeGuid": "handshake.a1b2c3d4-..." }
```

### Result Validation on `/callback-shepherd/done`

The `result` field is **required** on every `/callback-shepherd/done` request. The server
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
| `needs_iteration` | Reviewer | Check iteration counter: within budget → send new instructions to doer via TMUX `send-keys`, then send to reviewer. Exceeds `iteration.max` → `FailedToConvergeUseCase` |

---

## Server-Side Routing

A `SessionsState` class (owned by `TicketShepherd` — ref.ap.P3po8Obvcjw4IXsSUSU91.E) tracks
live `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E) instances, keyed by HandshakeGuid.
When a callback arrives:

1. Server looks up the HandshakeGuid in `SessionsState`
2. Finds the associated `TmuxAgentSession` and sub-part context
3. On `/callback-shepherd/done`: validates the `result` field against the sub-part's role
4. Routes the callback to `TicketShepherd` which decides what to do next
   (proceed to next sub-part, loop back, etc.)

This design naturally supports multiple concurrent agents — each has its own GUID and
its own `TmuxAgentSession` entry in `SessionsState`.

---

## Structured Text Delivery — Temp File Pattern

All structured/formatted content sent to agents goes through temp files:

- Write content to `$HOME/.shepherd_agent_harness/tmp/agent_comm/<unique_name>.md`
- Send file path to agent via TMUX `send-keys`: `"Read instructions at <path>"`
- **Exception**: Simple single-line messages (e.g., AgentSessionIdResolver GUID handshake) can be sent directly

---

## User-Question Flow — Attended Only (V1)

1. Agent calls `callback_shepherd.user-question.sh "How should I handle X?"`
2. Script POSTs to `/callback-shepherd/user-question` with `handshakeGuid` + question text
3. Server returns **200 immediately** — no blocking
4. **Agent waits** for the answer to arrive via TMUX (does not proceed)
5. Harness presents question to human (stdout/interactive)
6. Human answers (V1: human must be present; no autonomous fallback)
7. Harness writes answer to temp file (`$HOME/.shepherd_agent_harness/tmp/agent_comm/`)
8. Harness sends file path to agent via TMUX `send-keys`
9. Agent reads temp file, continues

**No long-lived HTTP connections.** The answer is delivered via TMUX, not via the HTTP response.
This follows the core principle: HTTP = agent→harness signaling, TMUX = harness→agent delivery.

---

## Callback Scripts

Four focused bash scripts, one per endpoint. Each script wraps a single curl call and is
the sole mechanism agents use to communicate back to the harness.

**Shared behavior across all scripts:**

- Lives on `$PATH` of the started agent
- Reads port from `$HOME/.shepherd_agent_harness/server/port.txt`
- Reads `$TICKET_SHEPHERD_HANDSHAKE_GUID` from environment; includes it in every request
- **Fail-fast:** hard-fail when `$TICKET_SHEPHERD_HANDSHAKE_GUID` is not set
- Expects 200 from server; exits non-zero on any other status code
- Agent receives usage instructions in its initial instructions, wrapped in
  `<critical_to_keep_through_compaction>` tags to survive context compaction

### Scripts

```bash
callback_shepherd.done.sh <result>             # required: completed | pass | needs_iteration
callback_shepherd.user-question.sh "<text>"    # required: question text
callback_shepherd.fail-workflow.sh "<reason>"  # required: failure reason (aborts entire workflow)
callback_shepherd.ping-ack.sh                  # no args — acknowledges health ping
```

**`callback_shepherd.done.sh` validates its argument** — rejects anything other than
`completed`, `pass`, or `needs_iteration` with a clear error and non-zero exit.
Server-side role validation is a second layer of defense.

---

## Harness → Agent Communication

TMUX `send-keys` is the **only** harness-to-agent communication channel. The harness uses it to:

- Deliver the HandshakeGuid for session ID resolution
- Send instruction file paths
- Send user-question answer file paths
- Send health pings
- Send iteration feedback instructions (on `needs_iteration`)

For the full agent lifecycle (spawn, session ID resolution), see
[`SpawnTmuxAgentSessionUseCase`](../use-case/SpawnTmuxAgentSessionUseCase.md) (ref.ap.hZdTRho3gQwgIXxoUtTqy.E).
