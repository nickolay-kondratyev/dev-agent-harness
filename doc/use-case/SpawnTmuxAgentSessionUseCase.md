# SpawnTmuxAgentSessionUseCase — Design / ap.hZdTRho3gQwgIXxoUtTqy.E

Orchestrates spawning an agent in a TMUX session, establishing identity via HandshakeGuid,
and resolving the agent's session ID.

**Implemented in**: `SpawnTmuxAgentSessionUseCase` (ref.ap.M1jzg6RlJkYL4hi8aXr7LnQA.E)

---

> Vocabulary: see [high-level.md Vocabulary](../high-level.md#vocabulary).
> Additional: **AgentSessionIdResolver** — interface that resolves agent-internal session IDs (e.g., Claude Code's JSONL filename) from a HandshakeGuid marker.

---

## HandshakeGuid

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the full HandshakeGuid specification — format, env var contract, and how it's used in every callback.

---

## Agent Callback Contract

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the callback contract, request payloads, and server-side routing via `SessionsState`.

---

## Session Schema in current_state.json

Session records follow the [Session Record Schema](../schema/plan-and-current-state.md#session-record-schema--apmwzgc1hykvwu3ijqbtew4e) (ref.ap.mwzGc1hYkVwu3IJQbTeW4.E) — the canonical definition for `sessionIds` entries including `handshakeGuid`, `agentSessionId`, `agentType`, `model`, and `timestamp`.

The last element in the `sessionIds` array is the current session. Session history is tracked
for V2 resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E).

---

## Spawn Flow — New Session

1. Harness generates a `HandshakeGuid` (`handshake.${UUID}`)
2. Harness reads `agentType` and `model` from sub-part config in `current_state.json` (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E)
3. Harness builds the TMUX start command:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 && claude --system-prompt-file <resolved_path> [flags]`
   — see [System Prompt File Resolution](#system-prompt-file-resolution) for how `<resolved_path>` is determined
4. Harness creates TMUX session running the command
5. Harness waits for agent startup (agent CLI needs time to initialize)
6. Harness sends GUID to agent via TMUX `send-keys` (plain text, directly)
7. `AgentSessionIdResolver` polls for the GUID in agent session artifacts
   (e.g., Claude Code JSONL files) and resolves the agent session ID
8. Harness stores a session record (ref.ap.mwzGc1hYkVwu3IJQbTeW4.E)
   in `current_state.json` under the sub-part's `sessionIds` array
9. Harness writes instruction file to `comm/in/instructions.md` in the sub-part's `.ai_out/` directory
10. Harness sends `"Read instructions at <path>"` via TMUX `send-keys`
11. Agent works (may call callback scripts for questions)
12. Agent calls `callback_shepherd.done.sh <result>` → server receives `/callback-shepherd/done` with GUID + result
13. Harness validates result against sub-part role, proceeds accordingly

### System Prompt File Resolution

Every `claude` invocation **must** include `--system-prompt-file`. This is not optional — omitting
it is a bug. The flag overrides Claude Code's built-in system prompt with a stage-specific prompt
file controlled by the harness operator.

**Resolution rules:**

| Stage | System prompt file |
|-------|-------------------|
| Planning (PLANNER, PLAN_REVIEWER) | `${MY_ENV}/config/claude/ai_input/system_prompt/for_planning.md` |
| Execution (all other agents) | `${MY_ENV}/config/claude/ai_input/system_prompt/default.md` |

- `MY_ENV` is a required environment variable (see [Required Environment Variables](../core/git.md#required-environment-variables)).
- Both system prompt files are validated at **harness initialization** — hard fail if either is missing.
  This follows the same fail-at-startup pattern as other required env vars.
- The caller (PartExecutor / TicketShepherd) knows the current stage and passes the resolved
  absolute path to `SpawnTmuxAgentSessionUseCase`.

---

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
---

## Agent Crash Recovery (V1)

**V1: no automatic recovery.** When `NoReplyToPingUseCase` detects an agent crash, the TMUX
session is killed, `signalDeferred` is completed with `AgentSignal.Crashed`, and
`TicketShepherd` delegates to `FailedToExecutePlanUseCase` — prints red error, halts, waits
for human intervention. V2 may add automatic retry with `--resume`
(ref.ap.LX1GCIjv6LgmM7AJFas20.E).

---

## Callback Scripts

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the `callback_shepherd.*.sh` scripts specification.

---

## TmuxAgentSession / ap.DAwDPidjM0HMClPDSldXt.E

The live session handle stored in `SessionEntry` (ref.ap.igClEuLMC0bn7mDrK41jQ.E).

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `sessionName` | `String` | TMUX session name. Format: `shepherd_${partName}_${subPartName}`. Used for `tmux send-keys` and `tmux kill-session`. |
| `handshakeGuid` | `HandshakeGuid` | The GUID assigned to this session (ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E). |
| `paneTarget` | `String` | TMUX pane target (e.g., `shepherd_main_impl:0.0`). Used for `send-keys` commands. |

### Kill Semantics

"Kill a TMUX session" means `tmux kill-session -t ${sessionName}`. This destroys the session
and all its windows/panes. Used by:
- `removeAllForPart` (on part completion)
- `NoReplyToPingUseCase` (on crash detection)
- Interrupt protocol Layer 2 (on user `kill` confirmation)

---

## Open Questions

- **Agent startup delay**: Currently a fixed duration (`agentStartupDelay`, default 5s).
  Open ticket `nid_827accci9acm2e320fvtddk13_E` to replace with callback-based readiness signal.
