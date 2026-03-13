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

## Bootstrap Handshake — Universal Protocol

The bootstrap handshake is the **same for both new agents and resumed agents**. It is the
foundational step that establishes the communication channel is working before any real work
begins. This makes the protocol robust — every agent session, whether fresh or resumed,
goes through the same identity + liveness verification.

### Bootstrap Message — Inline CLI Args

The bootstrap message is passed as **inline CLI arguments** (e.g., `-p "..."`), NOT via
TMUX `send-keys`. It is a lightweight, self-contained string — no instruction file needed.

Contents:
- The `HandshakeGuid` (so it's recorded in agent session artifacts for
  `AgentSessionIdResolver`)
- A single instruction: call `callback_shepherd.signal.sh started` to acknowledge startup

```bash
# New agent:
claude --system-prompt-file <path> -p "<bootstrap_message>"

# Resumed agent:
claude --resume <session_id> -p "<bootstrap_message>"
```

**Heavy instructions come later** via TMUX `send-keys` as a **file pointer**
(`"Read instructions at <path>"`) — only after the handshake succeeds.

---

## Spawn Flow — New Session

The spawn flow has two distinct phases: **bootstrap** (identity + liveness handshake) and
**work** (full instructions). Full instructions are only sent after the handshake succeeds.

### Phase 1: Bootstrap — Identity + Liveness Handshake

1. Harness generates a `HandshakeGuid` (`handshake.${UUID}`)
2. Harness reads `agentType` and `model` from sub-part config in `current_state.json` (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E)
3. Harness builds the TMUX start command with **bootstrap as inline CLI args**:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 && claude --system-prompt-file <resolved_path> [flags] -p "<bootstrap_message>"`
   — see [System Prompt File Resolution](#system-prompt-file-resolution) for `<resolved_path>`
4. Harness creates TMUX session running the command — agent starts with the bootstrap
   message as its initial prompt via CLI args (no `send-keys` needed)
5. Harness awaits `/callback-shepherd/signal/started` within `noStartupAckTimeout`
   (default 3 min) — see ref.ap.xVsVi2TgoOJ2eubmoABIC.E
6. On `/signal/started` received:
   a. **`AgentSessionIdResolver`** polls for the GUID in agent session artifacts
      (e.g., Claude Code JSONL files) — by this point the GUID is guaranteed to be recorded,
      so resolution is fast and reliable
   b. Harness stores a session record (ref.ap.mwzGc1hYkVwu3IJQbTeW4.E)
      in `current_state.json` under the sub-part's `sessionIds` array
   c. Agent is confirmed alive, env is correct, server is reachable → proceed to Phase 2
7. On timeout (no `/signal/started` within `noStartupAckTimeout`) → `NoStartupAckUseCase` →
   `PartResult.AgentCrashed`

### Phase 2: Work — Full Instructions

8. Harness writes instruction file to `comm/in/instructions.md` in the sub-part's `.ai_out/` directory
9. Harness sends `"Read instructions at <path>"` via TMUX `send-keys`
10. Agent works (may call callback scripts for questions)
11. Agent calls `callback_shepherd.signal.sh done <result>` → server receives `/callback-shepherd/signal/done` with GUID + result
12. Harness validates result against sub-part role, proceeds accordingly

## Resume Flow

Resuming an existing agent session uses the **same bootstrap handshake** — only the TMUX
start command differs (uses `--resume` instead of a fresh start).

1. Harness generates a **new** `HandshakeGuid` for this resumed session
2. Harness builds the TMUX start command with resume + bootstrap as inline CLI args:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 && claude --resume <session_id> -p "<bootstrap_message>"`
3–12. **Identical to new session flow** (steps 4–12 above)

The new HandshakeGuid ensures the server can distinguish this resumed session from the
previous one. The agent gets a fresh callback identity while retaining its conversation
history via `--resume`.

### Why Two Phases

- **No wasted work**: instruction assembly (which reads files, concatenates context) is
  deferred until the agent is confirmed alive. If spawn fails, no instruction file was written.
- **Clean separation**: the bootstrap validates the entire communication chain
  (TMUX → agent → env vars → HTTP → server routing) before any real work begins.
- **Universal**: same handshake for new and resumed agents — one protocol to test and debug.
- **Simpler session ID resolution**: `AgentSessionIdResolver` runs after `/started`, when
  the GUID is guaranteed to be in the JSONL — no race condition, no polling timeout risk.

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
string. The matching filename (minus `.jsonl` extension) is the session ID.

**Sequencing:** Resolution runs **after `/callback-shepherd/signal/started` is received** (step 6a
in the spawn flow). At this point, the agent has already processed the bootstrap message
containing the GUID, so the GUID is guaranteed to be in the JSONL file. This eliminates
the race condition that existed when polling concurrently with agent startup.

- Exactly one match required; zero (timeout) or multiple matches → `IllegalStateException`
- Default timeout: 45 seconds, poll interval: 500ms (generous — resolution is typically
  instant since the GUID is already written by the time we poll)
---

## Agent Crash Recovery (V1)

**V1: no automatic recovery.** When `NoReplyToPingUseCase` detects an agent crash, the TMUX
session is killed, `signalDeferred` is completed with `AgentSignal.Crashed`, and
`TicketShepherd` delegates to `FailedToExecutePlanUseCase` — prints red error, halts, waits
for human intervention. V2 may add automatic retry with `--resume`
(ref.ap.LX1GCIjv6LgmM7AJFas20.E).

---

## Callback Scripts

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for the `callback_shepherd.{signal|query}.sh` scripts specification.

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

## Resolved: Agent Startup Delay

~~Open question: replace fixed `agentStartupDelay` with callback-based readiness signal.~~

**Resolved:** The bootstrap message (GUID + `callback_shepherd.signal.sh started` instruction) is
passed as inline CLI args (`-p "..."`). The agent receives the bootstrap as its initial prompt
the moment it starts — no `send-keys` needed, no fixed delay. The harness awaits
`/callback-shepherd/signal/started` within `noStartupAckTimeout` (default 3 min). Same handshake
for new and resumed agents. See [Agent Startup Acknowledgment](../core/agent-to-server-communication-protocol.md#agent-startup-acknowledgment--apxvsvi2tgooj2eubmoabice)
(ref.ap.xVsVi2TgoOJ2eubmoABIC.E).
