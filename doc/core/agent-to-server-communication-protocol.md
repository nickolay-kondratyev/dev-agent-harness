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
┌────────────┐    HTTP (curl)     ┌──────────────────┐
│  Agent      │ ──────────────→  │  Harness Server   │
│  (in TMUX)  │                   │  (Ktor CIO)       │
│             │ ←──────────────  │                    │
│             │   TMUX send-keys  │                    │
└────────────┘                   └──────────────────┘
```

Communication is **bidirectional** through two distinct channels:

- **Agent → Harness**: HTTP POST via `harness-cli-for-agent.sh` (wraps curl). The agent CLI script handles port discovery and GUID injection transparently.
- **Harness → Agent**: TMUX `send-keys` is the only channel from harness to agent. Used for delivering instructions, Q&A answers, and health pings.

---

## HandshakeGuid — Agent Identity Key

### Problem

The server needs to know **which agent** is calling on every callback (`/agent/done`,
`/agent/question`, etc.). In a single-agent world this is trivial, but the design must
be multi-agent ready — multiple agents alive at the same time (e.g., implementor and
reviewer talking to each other).

### Solution

Every agent session gets a **HandshakeGuid** — a harness-generated identifier with format
`handshake.${UUID}`. The `handshake.` prefix makes GUIDs greppable in logs and
distinguishable from agent session IDs. This GUID:

1. Is exported as `TICKET_SHEPHERD_HANDSHAKE_GUID` env var when the TMUX session is spawned
2. Is sent to the agent as the first TMUX message (for AgentSessionIdResolver resolution)
3. Is included by the agent CLI in **every** callback to the server
4. Is stored in `current_state.json` alongside the agent's session ID

```kotlin
// HandshakeGuid value class — ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E
// Generation — always use the factory to enforce the prefix
val guid = HandshakeGuid.generate()
```

### How the Agent CLI Knows Its GUID

The TMUX session command exports the GUID before starting the agent:

```bash
export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.a1b2c3d4-... && claude
```

The agent CLI script reads `$TICKET_SHEPHERD_HANDSHAKE_GUID` from the environment and includes
it in every HTTP callback. The agent itself does not need to know about
the GUID — it is transparent, handled entirely by the CLI script.

**Fail-fast requirement:** The CLI must hard-fail when `$TICKET_SHEPHERD_HANDSHAKE_GUID` is not
set. This catches misconfigured spawns immediately.

---

## Server Port Discovery

The server binds to **port 0** (OS-assigned). On startup, it writes the assigned port to:

```
$HOME/.shepherd_agent_harness/server/port.txt
```

- `harness-cli-for-agent.sh` reads this file to construct the server URL
- On server shutdown, this file is **deleted**
- **No env var needed** — eliminates port collision risk entirely

The server starts once at harness startup and stays alive across all sub-parts.

---

## V1 Server Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /agent/done` | Agent completed its task. TMUX session stays alive; killed only when the part completes. |
| `POST /agent/question` | Agent has a question. Curl blocks until human answers. Answer delivered via TMUX send-keys (temp file). |
| `POST /agent/failed` | Unrecoverable error. Triggers `FailedToExecutePlanUseCase`. |
| `POST /agent/status` | Agent responds to health ping (see Agent Health Monitoring in [high-level.md](../high-level.md)). |

---

## Agent Callback Contract + Request Payloads

All agent-to-server HTTP requests include `handshakeGuid` as the identity field.
No `branch` field — the server derives branch and sub-part context from its internal
GUID-to-sub-part registry.

### Request Payloads

```json
// POST /agent/done
{ "handshakeGuid": "handshake.a1b2c3d4-..." }

// POST /agent/question
{ "handshakeGuid": "handshake.a1b2c3d4-...", "question": "How should I handle X?" }

// POST /agent/failed
{ "handshakeGuid": "handshake.a1b2c3d4-...", "reason": "Cannot compile" }

// POST /agent/status
{ "handshakeGuid": "handshake.a1b2c3d4-..." }
```

---

## Server-Side Routing

A `SessionsState` class (owned by `TicketShepherd` — ref.ap.P3po8Obvcjw4IXsSUSU91.E) tracks
live `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E) instances, keyed by HandshakeGuid.
When a callback arrives:

1. Server looks up the HandshakeGuid in `SessionsState`
2. Finds the associated `TmuxAgentSession` and sub-part context
3. Routes the callback to `TicketShepherd` which decides what to do next
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

## Q&A Flow — Attended Only (V1)

1. Agent calls `harness-cli question "How should I handle X?"`
2. CLI POSTs to `/agent/question` with `handshakeGuid` + question text
3. Harness presents question to human (stdout/interactive)
4. Human answers (V1: human must be present; no autonomous fallback)
5. Harness writes answer to temp file (`$HOME/.shepherd_agent_harness/tmp/agent_comm/`)
6. Harness sends file path to agent via TMUX `send-keys`
7. Harness responds 200 to the blocked curl (unblocking agent CLI script)
8. Agent reads temp file, continues

---

## Agent CLI Script

**`harness-cli-for-agent.sh`** — a bash script wrapping curl calls. It is the sole mechanism
agents use to communicate back to the harness.

- Lives on `$PATH` of the started agent
- Reads port from `$HOME/.shepherd_agent_harness/server/port.txt`
- Reads `$TICKET_SHEPHERD_HANDSHAKE_GUID` from environment; includes it in every request
- Agent receives `--help` content in its instructions, wrapped in
  `<critical_to_keep_through_compaction>` tags to survive context compaction

**Commands:**

```
harness-cli done                    # Signal task completion
harness-cli question "<text>"       # Ask human a question (answer delivered via TMUX)
harness-cli failed "<reason>"       # Signal unrecoverable failure
harness-cli status                  # Reply to health ping
```

**Fail-fast:** If `TICKET_SHEPHERD_HANDSHAKE_GUID` is not set, the script must exit with a
clear error. This catches misconfigured spawns immediately.

---

## Harness → Agent Communication

TMUX `send-keys` is the **only** harness-to-agent communication channel. The harness uses it to:

- Deliver the HandshakeGuid for session ID resolution
- Send instruction file paths
- Send Q&A answer file paths
- Send health pings

For the full agent lifecycle (spawn, resume, session ID resolution), see
[`SpawnTmuxAgentSessionUseCase`](../use-case/SpawnTmuxAgentSessionUseCase.md) (ref.ap.hZdTRho3gQwgIXxoUtTqy.E).
