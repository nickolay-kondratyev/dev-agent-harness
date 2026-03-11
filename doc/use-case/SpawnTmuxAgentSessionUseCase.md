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
| **HandshakeGuid**               | A harness-generated identifier (`handshake.${UUID}`) for agent identity. See [protocol doc](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E). |
| **AgentSessionIdResolver**      | Interface that resolves agent-internal session IDs (e.g., Claude Code's JSONL filename) from a HandshakeGuid marker.                                                                                     |

---

## HandshakeGuid

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the full HandshakeGuid specification — format, env var contract, and how it's used in every callback.

---

## Agent Callback Contract

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the callback contract, request payloads, and server-side routing via `SessionsState`.

---

## Session Schema in current_state.json

Session records follow the [Session Record Schema](../schema/plan-and-current-state.md#session-record-schema--apmwzgc1hykvwu3ijqbtew4e) (ref.ap.mwzGc1hYkVwu3IJQbTeW4.E) — the canonical definition for `sessionIds` entries including `handshake_guid`, `agent_session_id`, `agentType`, `model`, and `timestamp`.

**Resume = use the last element** in the `sessionIds` array. The `agent_session_id` (or
`agent_session_path`) plus `model` from that entry are used for the `--resume` invocation.

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
8. Harness stores a session record (ref.ap.mwzGc1hYkVwu3IJQbTeW4.E)
   in `current_state.json` under the sub-part's `sessionIds` array
9. Harness writes instruction file to temp file
10. Harness sends `"Read instructions at <path>"` via TMUX `send-keys`
11. Agent works (may call callback scripts for questions)
12. Agent calls `callback_shepherd.done.sh <result>` → server receives `/callback-shepherd/done` with GUID + result
13. Harness validates result against sub-part role, proceeds accordingly

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

## Callback Scripts

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the `callback_shepherd.*.sh` scripts specification.

---

## Open Questions

- **Agent startup delay**: Currently a fixed duration (`agentStartupDelay`, default 5s).
  Open ticket `nid_827accci9acm2e320fvtddk13_E` to replace with callback-based readiness signal.
