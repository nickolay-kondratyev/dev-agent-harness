# SpawnTmuxAgentSessionUseCase — Design / ap.hZdTRho3gQwgIXxoUtTqy.E

Orchestrates spawning an agent in a TMUX session, establishing identity via HandshakeGuid,
and resolving the agent's session ID for future resume.

**Implemented in**: `SpawnTmuxAgentSessionUseCase` (ref.ap.M1jzg6RlJkYL4hi8aXr7LnQA.E)

---

## Vocabulary

| Term                            | Definition                                                                                                                                                                                               |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ShepherdServer** (aka Server) | The long-lived HTTP server instance that starts at harness launch and handles all requests from agents. One per harness process.                                                                         |
| **Agent**                       | An instance of a code agent (e.g., Claude Code, PI) running in a TMUX session. In the future, multiple agents may be alive simultaneously.                                                               |
| **HandshakeGuid**               | A harness-generated identifier (`handshake.${UUID}`) assigned to each agent session. Used in all agent↔server communication. The agent receives it as an env var; the server uses it to route callbacks. |
| **AgentSessionIdResolver**      | Interface that resolves agent-internal session IDs (e.g., Claude Code's JSONL filename) from a HandshakeGuid marker.                                                                                     |

---

## HandshakeGuid — Agent Identity Key

### Problem

The server needs to know **which agent** is calling on every callback (`/agent/done`,
`/agent/question`, etc.). In a single-agent world, this is trivial. But the design must
be multi-agent ready — multiple agents alive at the same time (example implementor and reviewer talking to each other).

### Solution

Every agent session gets a **HandshakeGuid** — a harness-generated identifier with format
`handshake.${UUID}`. This GUID:

1. Is exported as `TICKET_SHEPHERD_HANDSHAKE_GUID` env var when the TMUX session is spawned
2. Is sent to the agent as the first TMUX message (for AgentSessionIdResolver resolution)
3. Is included by the agent CLI in **every** callback to the server
4. Is stored in `current_state.json` alongside the agent's session ID

The `handshake.` prefix makes GUIDs greppable in logs and distinguishable from agent session IDs.

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
it in every HTTP callback to the server. The agent itself does not need to know about
the GUID — it's transparent, handled entirely by the CLI script.

The CLI should hard fail when $TICKET_SHEPHERD_HANDSHAKE_GUID is not found.

---

## Agent Callback Contract

All agent→server HTTP requests include `handshakeGuid` as the identity field.
No `branch` field — the server derives branch and sub-part context from its internal
GUID→sub-part registry.

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

### Server-Side Coordination

A `SessionsState` class tracks live `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E)
instances, keyed by HandshakeGuid. When a callback arrives:

1. Server looks up the HandshakeGuid in `SessionsState`
2. Finds the associated `TmuxAgentSession` and sub-part context
3. Routes the callback to `TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E)
   which decides what to do next (proceed to next sub-part, loop back, etc.)

This design naturally supports multiple concurrent agents: each has its own GUID,
its own `TmuxAgentSession` entry in `SessionsState`.

---

## Session Schema in current_state.json

Each session record in the `sessionIds` array contains:

```json
{
  "name": "1_impl",
  "role": "IMPLEMENTOR",
  "sessionIds": [
    {
      "handshake_guid": "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "agent_session_id": "77d5b7ea-cf04-453b-8867-162404763e18",
      "agent_session_path": null,
      "agentType": "ClaudeCode",
      "timestamp": "2026-03-10T15:30:00Z"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `handshake_guid` | The harness-generated GUID for this session. Our identifier — used in all communication. |
| `agent_session_id` | The agent's internal session ID (e.g., Claude Code JSONL filename UUID). Used for `--resume`. |
| `agent_session_path` | Alternative to `agent_session_id` for agents that use paths (e.g., PI). Null when not applicable. |
| `agentType` | Which agent implementation (e.g., `"ClaudeCode"`, `"PI"`). |
| `timestamp` | ISO-8601 timestamp of session creation. |

**Resume = use the last element** in the `sessionIds` array. The `agent_session_id` (or
`agent_session_path`) from that entry is used for the `--resume` flag.

---

## Spawn Flow — New Session

1. Harness generates a `HandshakeGuid` (`handshake.${UUID}`)
2. Harness chooses agent type (from sub-part config or role catalog)
3. Harness builds the TMUX start command:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && claude [flags]`
4. Harness creates TMUX session running the command
5. Harness waits for agent startup (agent CLI needs time to initialize)
6. Harness sends GUID to agent via TMUX `send-keys` (plain text, directly)
7. `AgentSessionIdResolver` polls for the GUID in agent session artifacts
   (e.g., Claude Code JSONL files) and resolves the agent session ID
8. Harness stores `{ handshake_guid, agent_session_id, agentType, timestamp }`
   in `current_state.json` under the sub-part's `sessionIds` array
9. Harness writes instruction file to temp file
10. Harness sends `"Read instructions at <path>"` via TMUX `send-keys`
11. Agent works (may call CLI for questions)
12. Agent calls `harness-cli done` → server receives `/agent/done` with GUID
13. Harness kills TMUX session, proceeds to next sub-part

### AgentSessionIdResolver

**Problem:** Claude Code doesn't expose its session ID to the agent itself.

**Solution:** `AgentSessionIdResolver` interface (ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E)
+ `ClaudeCodeAgentSessionIdResolver` implementation (ref.ap.gCgRdmWd9eTGXPbHJvyxI.E).

The resolver polls `$HOME/.claude/projects/.../*.jsonl` for files containing the GUID
string. The matching filename (minus `.jsonl` extension) is the session ID. Polling is
necessary because Claude Code writes the JSONL file asynchronously after receiving
the TMUX message.

- Exactly one match required; zero (timeout) or multiple matches → `IllegalStateException`
- Default timeout: 45 seconds, poll interval: 500ms
- **Not used during resume** — session ID already known

---

## Spawn Flow — Resume Session (after crash)

1. Harness reads last `sessionIds` entry from `current_state.json`
2. Harness builds the TMUX start command:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && claude --resume <agent_session_id>`
3. Harness creates TMUX session running the command
4. Skip AgentSessionIdResolver — session ID already known
5. Harness writes instruction file, sends path via TMUX `send-keys`
6. Flow continues from step 11 of the new session flow

Note: on resume, the **same HandshakeGuid** from the original session is reused so
the server-side registry remains valid.

---

## Agent CLI Script (spec — implementation pending)

**`harness-cli-for-agent.sh`** — bash script wrapping curl calls.

- Lives on `$PATH` of the started agent
- Reads port from `$HOME/.shepherd_agent_harness/server/port.txt`
- Reads `$TICKET_SHEPHERD_HANDSHAKE_GUID` from environment, includes in every request
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

## Open Questions

- **Agent startup delay**: Currently a fixed duration (`agentStartupDelay`, default 5s).
  Open ticket `nid_827accci9acm2e320fvtddk13_E` to replace with callback-based readiness signal.
