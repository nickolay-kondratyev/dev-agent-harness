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

### Bootstrap Message — Initial Prompt Argument

The bootstrap message is delivered as an **initial prompt argument** in the CLI command that
starts the agent. Agents must NOT be spawned with `-p`/`--print` flags — those produce
non-interactive sessions and defeat the purpose of TMUX (see Hard Constraints in
[high-level.md](../high-level.md#hard-constraints)).

The agent is started interactively with the bootstrap message embedded as a positional
argument in the start command. This eliminates timing guesswork — the agent receives the
bootstrap atomically on startup, with no gap between "session created" and "first input
delivered." The bootstrap is a lightweight, self-contained string — no instruction file needed.

Contents:
- The `HandshakeGuid` (so it's recorded in agent session artifacts for
  `AgentSessionIdResolver`)
- A single instruction: call `callback_shepherd.signal.sh started` to acknowledge startup

```bash
# New agent — interactive start with bootstrap as initial prompt (no -p):
export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 \
  && claude --system-prompt-file <resolved_path> [flags] "<bootstrap_message>"

# Resumed agent — interactive resume with bootstrap as initial prompt (no -p):
export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 \
  && claude --resume <session_id> "<bootstrap_message>"
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
3. Harness builds the TMUX start command for **interactive mode** (no `-p`) with the
   **bootstrap message as initial prompt argument**:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 && claude --system-prompt-file <resolved_path> [flags] "<bootstrap_message>"`
   — see [System Prompt File Resolution](#system-prompt-file-resolution) for `<resolved_path>`
4. Harness creates TMUX session running the command — agent starts in **interactive mode**
   and immediately receives the bootstrap message as its first input (no separate `send-keys`
   step needed)
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
9. Harness wraps `"Read instructions at <path>"` in the Payload Delivery ACK wrapper
   (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E), generates a `PayloadId`, sets `pendingPayloadAck`
   on `SessionEntry`
10. Harness sends wrapped payload via TMUX `send-keys`
11. Agent reads XML wrapper, calls `callback_shepherd.signal.sh ack-payload <PayloadId>`,
    then processes the instruction content
12. Harness confirms ACK (clears `pendingPayloadAck`), enters health-aware signal-await loop.
    If no ACK within 3 min → retries `send-keys` (up to 2 retries, 3 total attempts)
13. Agent works (may call callback scripts for questions)
14. Agent calls `callback_shepherd.signal.sh done <result>` → server receives `/callback-shepherd/signal/done` with GUID + result
15. Harness validates result against sub-part role, proceeds accordingly

## Resume Flow

Resuming an existing agent session uses the **same bootstrap handshake** — only the TMUX
start command differs (uses `--resume` instead of a fresh start).

1. Harness generates a **new** `HandshakeGuid` for this resumed session
2. Harness builds the TMUX start command for **interactive resume** (no `-p`) with
   **bootstrap as initial prompt argument**:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 && claude --resume <session_id> "<bootstrap_message>"`
3. Harness creates TMUX session running the command — agent resumes in **interactive mode**
   and immediately receives the bootstrap message as its first input
4–12. **Identical to new session flow** (steps 5–12 above)

The new HandshakeGuid ensures the server can distinguish this resumed session from the
previous one. The agent gets a fresh callback identity while retaining its conversation
history via `--resume`.

### Why Two Phases

- **No wasted work**: instruction assembly (which reads files, concatenates context) is
  deferred until the agent is confirmed alive. If spawn fails, no instruction file was written.
- **Clean separation**: the bootstrap validates the entire communication chain
  (agent CLI → env vars → HTTP → server routing) before any real work begins.
- **No timing guesswork**: the bootstrap message is the agent's initial prompt argument —
  delivered atomically on startup. When `/started` arrives, the agent has proven it can
  process input, making subsequent `send-keys` (Phase 2) safe by demonstration.
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

### AgentStarter — Interface for Start Command
<!-- ap.RK7bWx3vN8qLfYtJ5dZmQ.E -->

**Problem:** Different agent types (Claude Code, PI, future agents) have entirely different
CLI invocations — flags, env vars, nested-session workarounds, system prompt mechanisms.
The spawn flow must not hard-code any agent-specific command construction.

**Solution:** `AgentStarter` **interface** — each agent type provides its own implementation
that knows how to build the shell command for TMUX session creation.

```kotlin
interface AgentStarter {
    fun buildStartCommand(bootstrapMessage: String): TmuxStartCommand
}
```

#### Why an Interface

1. **Different agent types have different start commands.** Claude Code uses `claude --system-prompt-file <path> [flags]`
   (interactive mode); a PI agent will have an entirely different binary and flags. The interface allows each
   `AgentType` to provide its own starter without modifying existing code (OCP).
2. **Agent-specific concerns are encapsulated.** Claude Code needs `unset CLAUDECODE` to avoid
   nested-session detection; other agents won't. These details belong in the implementation,
   not in `SpawnTmuxAgentSessionUseCase`.
3. **Testability.** Unit tests inject a fake starter — no real CLI invocations. The interface
   boundary is what makes `SpawnTmuxAgentSessionUseCase` independently testable.

#### V1 Implementation — ClaudeCodeAgentStarter

Builds the `claude` CLI command for **interactive mode** (no `-p`/`--print`):
- `TICKET_SHEPHERD_HANDSHAKE_GUID` and `TICKET_SHEPHERD_SERVER_PORT` env var exports
- `cd` to working directory
- `unset CLAUDECODE` (nested-session detection workaround)
- `--system-prompt-file <path>` — **required** (see [System Prompt File Resolution](#system-prompt-file-resolution))
- Model, tools, dangerously-skip-permissions flags
- For resumed sessions: `--resume <session_id>`
- Bootstrap message embedded as a positional initial prompt argument
- **No `-p` flag** — agent starts interactively

#### Dispatch

`SpawnTmuxAgentSessionUseCase` reads `agentType` from the sub-part config in `current_state.json`
and selects the matching `AgentStarter` implementation. The pair of `AgentStarter` +
`AgentSessionIdResolver` per agent type forms the complete agent-type-specific contract.

---

### AgentSessionIdResolver — Interface, Not Implementation

**Problem:** Claude Code does **not** expose its session ID to the agent from within its own
context. There is no API, env var, or CLI flag that an agent running inside Claude Code can
call to learn its own session ID. **This has been validated empirically** — it is a confirmed
limitation, not a guess.

Yet the harness needs the session ID for:
- Persisting session records in `current_state.json` (inspectable, git-tracked)
- V2 resume via `--resume <session_id>` (ref.ap.LX1GCIjv6LgmM7AJFas20.E)
- Correlating TMUX sessions with agent artifacts for debugging

**Solution:** `AgentSessionIdResolver` **interface** (ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E)
+ `ClaudeCodeAgentSessionIdResolver` **implementation** (ref.ap.gCgRdmWd9eTGXPbHJvyxI.E).

#### Why an Interface

`AgentSessionIdResolver` is an **interface by design** — not fragile coupling:

1. **Different agent types have different session ID discovery mechanisms.** Claude Code uses
   JSONL files; a future PI agent or other agent implementation will have entirely different
   artifacts. The interface allows each `AgentType` to provide its own resolver without
   modifying existing code (OCP).
2. **Testability.** Unit tests inject a fake resolver — no filesystem scanning, no real
   Claude Code sessions. The interface boundary is what makes `SpawnTmuxAgentSessionUseCase`
   independently testable.
3. **The resolver is NOT internal coupling** — it's the harness extracting externally observable
   artifacts (JSONL files that Claude Code writes). The agent is unaware of the resolver.

#### Resolution Mechanism (ClaudeCode)

The `ClaudeCodeAgentSessionIdResolver` polls `$HOME/.claude/projects/.../*.jsonl` for files
containing the GUID string. The matching filename (minus `.jsonl` extension) is the session ID.

**Sequencing:** Resolution runs **after `/callback-shepherd/signal/started` is received** (step 6a in the spawn flow). At this point, the agent has already processed the bootstrap message
containing the GUID, so the GUID is guaranteed to be in the JSONL file. This eliminates
the race condition that existed when polling concurrently with agent startup.

- Exactly one match required; zero (timeout) or multiple matches → `IllegalStateException`
- Default timeout: 45 seconds, poll interval: 500ms (generous — resolution is typically
  instant since the GUID is already written by the time we poll)

#### Integration Testing — ClaudeCodeAgentSessionIdResolver

`ClaudeCodeAgentSessionIdResolver` **must** be validated by integration tests against a real
Claude Code session. This confirms:

- The JSONL file format assumption still holds (Claude Code could change it across versions)
- GUID matching works end-to-end (env var → bootstrap message → JSONL write → scan → resolve)

**Resource efficiency:** Session ID resolution testing should be part of a **broader
integration test that spawns a single real agent session** and validates multiple concerns
from that one session (e.g., bootstrap handshake, session ID resolution, callback protocol).
Spawning a separate agent session just for resolver testing would be wasteful.

#### Value of Session Recording

Session IDs are recorded in `current_state.json` even in V1 (which does not support resume).
This is intentional — recorded sessions are valuable for **post-hoc inspection**: debugging
agent behavior, auditing what happened in a run, and correlating TMUX session output with
agent artifacts. Resume is an additional (V2) benefit, not the only reason to record.
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

Uses a **compositional design** that groups related concerns into nested types for better
encapsulation and type safety.

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `tmuxSession` | `TmuxSession` | Live TMUX session handle for sending keys and checking existence. See [TmuxSession fields](#tmuxsession-fields) below. |
| `resumableAgentSessionId` | `ResumableAgentSessionId` | Agent session identity for persistence and V2 resume. See [ResumableAgentSessionId fields](#resumableagentid-fields) below. |

### TmuxSession Fields

| Field | Type | Description |
|-------|------|-------------|
| `name` | `TmuxSessionName` | TMUX session name. Format: `shepherd_${partName}_${subPartName}`. Used for `tmux kill-session`. |
| `paneTarget` | `String` | TMUX pane target (e.g., `shepherd_main_impl:0.0`). Used for `send-keys` commands. |

### ResumableAgentSessionId Fields

| Field | Type | Description |
|-------|------|-------------|
| `handshakeGuid` | `HandshakeGuid` | The GUID assigned to this session (ref.ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E). Used for server routing via `SessionsState`. |
| `agentType` | `AgentType` | Which agent implementation (e.g., `ClaudeCode`, `PI`). Used for dispatching to the correct `AgentStarter` and `AgentSessionIdResolver`. |
| `sessionId` | `String` | The agent's internal session ID (e.g., Claude Code JSONL filename UUID). Used for V2 `--resume`. |
| `model` | `String` | The actual model name used for this session (e.g., `sonnet`, `opus`). Must match the sub-part's `model` field. |

### Kill Semantics

"Kill a TMUX session" means `tmux kill-session -t ${sessionName}`. This destroys the session
and all its windows/panes. Used by:
- `removeAllForPart` (on part completion)
- `NoReplyToPingUseCase` (on crash detection)
- Interrupt protocol (on user kill confirmation — ref.ap.P3po8Obvcjw4IXsSUSU91.E)

---

## Resolved: Agent Startup Delay

~~Open question: replace fixed `agentStartupDelay` with callback-based readiness signal.~~

**Resolved:** The agent is spawned in **interactive mode** (no `-p`). The bootstrap message
(GUID + `callback_shepherd.signal.sh started` instruction) is delivered as an **initial
prompt argument** in the CLI command — no separate `send-keys` step, no timing guesswork.
The harness awaits `/callback-shepherd/signal/started` within `noStartupAckTimeout`
(default 3 min) — no fixed delay. Same handshake for new and resumed agents. See
[Agent Startup Acknowledgment](../core/agent-to-server-communication-protocol.md#agent-startup-acknowledgment--apxvsvi2tgooj2eubmoabice)
(ref.ap.xVsVi2TgoOJ2eubmoABIC.E).
