# Agent Health Monitoring — UseCase Pattern / ap.RJWVLgUGjO5zAwupNLhA0.E

Timeout + ping mechanism to detect crashed/hung agents. Failure scenarios are encapsulated
in UseCase classes — simple, stateless, single-responsibility operations that the `PartExecutor`
(ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) invokes from its health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E).

Liveness is determined **solely by HTTP callback timestamps** (`lastActivityTimestamp`).
The `context_window_slim.json` file is used for **context window compaction decisions only**
(ref.ap.8nwz2AHf503xwq8fKuLcl.E), not for liveness detection.

---

## Logging Principle

**All health monitoring decisions MUST be logged with structured values.** Every check,
every threshold comparison, every action taken — logged. Health monitoring operates in
the background and its decisions can kill sessions; there must be a full audit trail.

Examples of required log points:
- `"last_activity_timestamp_fresh — no action needed"` with `Val(callbackAge, CALLBACK_AGE)`
- `"sending_health_ping"` with `Val(sessionName, TMUX_SESSION_NAME)`, `Val(staleDuration, STALE_DURATION)`
- `"crash_detected — no activity after ping"` with `Val(sessionName, TMUX_SESSION_NAME)`

This logging requirement applies to all health monitoring logic described below.

---

## Flow

0. **Startup acknowledgment** (`healthTimeouts.startup`, default: 3 min): After spawn, the executor uses a shorter startup window. If no callback of any kind (`/callback-shepherd/signal/started`, `/callback-shepherd/signal/done`, etc.) arrives within this window → triggers `AgentUnresponsiveUseCase` with `DetectionContext.STARTUP_TIMEOUT`. This catches agent startup failures (bad env, binary crash, TMUX issues) in 3 minutes instead of 30. Once any callback arrives, the executor switches to the `normalActivity` window. See [Agent Startup Acknowledgment](../core/agent-to-server-communication-protocol.md#agent-startup-acknowledgment--apxvsvi2tgooj2eubmoabice) (ref.ap.xVsVi2TgoOJ2eubmoABIC.E).
0b. **Payload delivery ACK** (default: 3 min per attempt, 3 attempts max): After sending instructions via `send-keys`, the executor awaits `/signal/ack-payload` before entering the signal-await loop. If no ACK → retry `send-keys` (up to 2 retries). All retries exhausted → `AgentCrashed`. This catches the "alive but never received instruction" failure mode. See [Payload Delivery ACK Protocol](../core/agent-to-server-communication-protocol.md#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe) (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).
1. **No activity timeout** (`healthTimeouts.normalActivity`, default: 30 min): If no HTTP callback of any kind arrives within this window → triggers `AgentUnresponsiveUseCase` with `DetectionContext.NO_ACTIVITY_TIMEOUT`. Activity is determined by `SessionEntry.lastActivityTimestamp` (ref.ap.igClEuLMC0bn7mDrK41jQ.E) — updated on every HTTP callback.
2. **Ping**: Harness sends a message to agent via TMUX send-keys asking if it's still running and needs more time. Agent is expected to reply via `callback_shepherd.signal.sh ping-ack`
3. **Ping response timeout** (`healthTimeouts.pingResponse`, default: 3 min): After ping, re-check `lastActivityTimestamp`. If **any** callback arrived during the ping window, the agent is alive (proof of life) — loop back to step 1. If no callback → triggers `AgentUnresponsiveUseCase` with `DetectionContext.PING_TIMEOUT`.
4. **Crash handling (V1)**: `AgentUnresponsiveUseCase` (with `PING_TIMEOUT` context) kills TMUX session, completes `signalDeferred` with `AgentSignal.Crashed` → executor returns `PartResult.AgentCrashed` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts — waits for human intervention). **No automatic recovery in V1.** V2 may add retry with `--resume` (ref.ap.LX1GCIjv6LgmM7AJFas20.E).

---

## Monitoring Loop — Executor-Owned / ap.6HIM68gd4kb8D2WmvQDUK.E

The health monitoring loop is **owned by the executor**, not a separate background component.
This eliminates race conditions between the monitor and the server competing to complete the
same `CompletableDeferred<AgentSignal>`. See executor health-aware await loop at
ref.ap.QCjutDexa2UBDaKB3jTcF.E.

### Why Executor-Owned

- **Single control flow**: The executor creates the deferred, registers it, and awaits it.
  Making the executor also responsible for health checks keeps a single owner for the deferred
  lifecycle. No separate coroutine competing to `complete()` the deferred.
- **Structured concurrency**: The health check is naturally scoped to the executor's lifetime.
  When the executor finishes (signal received or crash detected), the monitoring stops. No
  cleanup of orphaned background coroutines.
- **Timer reset via shared state**: The server updates `SessionEntry.lastActivityTimestamp`
  (ref.ap.igClEuLMC0bn7mDrK41jQ.E) on every callback. The executor reads this timestamp on
  each health check tick. No direct communication channel needed between server and monitor.

### Proof-of-Life Principle

After sending a ping, the executor does **not** require a specific `/ping-ack` response.
Instead, it re-checks `lastActivityTimestamp` after the ping timeout window. If **any**
callback arrived during that window (`user-question`, `done`, `ping-ack`,
etc.), the agent is demonstrably alive. `/ping-ack` exists as a fallback for agents that
are alive but idle (no other callbacks to send) — which is exactly the scenario health
monitoring aims to detect.

### Liveness Model: HTTP Callbacks Only / ap.dnc1m7qKXVw2zJP8yFRE.E

Liveness is determined **solely** by HTTP callback timestamps (`lastActivityTimestamp`).
The `context_window_slim.json` file is used for **context window compaction decisions only**
(ref.ap.8nwz2AHf503xwq8fKuLcl.E), not for liveness detection.

| Signal | Source | Purpose |
|--------|--------|---------|
| `lastActivityTimestamp` | Agent → Server HTTP callbacks | **Liveness detection** — updated on every callback (started, done, ping-ack, ack-payload, user-question, self-compacted) |
| `context_window_slim.json` | External hook (ref.ap.ufavF1Ztk6vm74dLAgANY.E) | **Compaction decisions only** — remaining_percentage drives self-compaction thresholds (ref.ap.8nwz2AHf503xwq8fKuLcl.E). NOT used for liveness. |

#### Simplification Tradeoff

A previous design used a dual-signal liveness model combining HTTP callbacks and
`fileUpdatedTimestamp` from `context_window_slim.json`. This enabled early ping detection
(~8 min vs ~33 min for in-session agent death where TMUX is alive but agent process exited).

**We chose the simpler single-signal model because:**
- The in-session agent death scenario (TMUX alive, agent process dead) is **unlikely** in
  normal operation — agent processes are long-lived and managed by TMUX.
- The tradeoff is that we wait longer to detect this specific failure (~33 min instead of
  ~8 min). This is **acceptable** for a rare edge case.
- Removing the dual-signal model eliminates a 4-case decision matrix, the `effectiveLastActivity`
  concept, ping suppression logic, early ping logic, and the `contextFileStaleTimeout` parameter
  from health monitoring — significantly reducing complexity.

### Configuration

Health timeout configuration is managed through `HarnessTimeoutConfig`
(`com.glassthought.shepherd.core.data.HarnessTimeoutConfig`), injected via
`ShepherdContext.timeoutConfig`; tests use `HarnessTimeoutConfig.forTests()` for fast timeouts.

#### HealthTimeoutLadder

The three health-check timeouts form a conceptual ladder — startup → steady-state → ping-response.
They are grouped into a single data class so operators see the full sequence in one place:

```kotlin
data class HealthTimeoutLadder(
    val startup: Duration = 3.minutes,          // catch spawn failures before first callback
    val normalActivity: Duration = 30.minutes,  // steady-state liveness window
    val pingResponse: Duration = 3.minutes      // proof-of-life window after ping
)
```

The ordering is intentional: `startup` should be ≤ `normalActivity`; `pingResponse` is typically
≤ `startup`. Test configurations use a short ladder, e.g.
`HealthTimeoutLadder(startup = 1.second, normalActivity = 5.seconds, pingResponse = 1.second)`.

Accessed as `HarnessTimeoutConfig.healthTimeouts: HealthTimeoutLadder`.

#### All Health Parameters

| Parameter | Source | Default | Description |
|-----------|--------|---------|-------------|
| `startup` | `healthTimeouts.startup` | 3 min | Time after spawn before declaring startup failure — catches misconfigured env, binary crashes. Applies only until the first callback arrives, then switches to `normalActivity`. |
| `healthCheckInterval` | `HarnessTimeoutConfig.healthCheckInterval` | 5 min | How often the executor checks `lastActivityTimestamp` while awaiting the deferred |
| `normalActivity` | `healthTimeouts.normalActivity` | 30 min | Elapsed time since last HTTP callback before triggering a ping. Single global value — applies to all sub-parts. |
| `pingResponse` | `healthTimeouts.pingResponse` | 3 min | Time to wait after ping before declaring crash (any callback activity resets) |

### What Runs Where

| Concern | Owner | Mechanism |
|---------|-------|-----------|
| Update `lastActivityTimestamp` | `ShepherdServer` | On every incoming callback (including `/started`) |
| Check startup ack timeout | `PartExecutor` | Health-aware await loop — uses `healthTimeouts.startup` until first callback arrives |
| Check `lastActivityTimestamp` staleness, trigger ping | `PartExecutor` | Health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) — `lastActivityTimestamp` stale > `healthTimeouts.normalActivity` |
| Send ping message via TMUX | `PartExecutor` | Via `AgentFacade.sendHealthPing()` — delegates internally to `AgentUnresponsiveUseCase` (`NO_ACTIVITY_TIMEOUT` context) |
| Post-ping check | `PartExecutor` | After `healthTimeouts.pingResponse` window: check `lastActivityTimestamp` for advancement |
| Declare crash, kill TMUX | `PartExecutor` | Via `AgentFacade.killSession()` — delegates internally to `AgentUnresponsiveUseCase` (`PING_TIMEOUT` context) |
| Complete deferred with `Crashed` | `PartExecutor` | After kill session executes |
| Complete deferred with `Done`/`FailWorkflow` | `ShepherdServer` | On `/done` or `/fail-workflow` callback (via `SessionsState` internal to `AgentFacadeImpl`) |

### Testability

The health monitoring logic is the most timing-sensitive code in the system. It is unit-tested
via `FakeAgentFacade` + virtual time (`TestClock` + `kotlinx-coroutines-test`):

- `FakeAgentFacade` controls `lastActivityTimestamp` advancement
- `TestClock` (ref.ap.whDS8M5aD2iggmIjDIgV9.E) controls `now()` for timestamp age comparisons
- `advanceTimeBy()` controls coroutine delays (1-second ticks, timeout windows)
- Tests verify every decision branch: standard ping, crash declaration, proof-of-life acceptance

See [Testing Strategy](../high-level.md#testing-strategy--fake-driven-unit-coverage) in
high-level.md for the full testing approach.

---

## UseCase Classes

| UseCase | Trigger | Action |
|---|---|---|
| `AgentUnresponsiveUseCase` | Agent fails to respond — see `DetectionContext` below | Parameterized by `DetectionContext`. Logs structured context (detection reason, session name, durations). Action depends on context — see table below. Single class, single failure-handling path for all unresponsive-agent scenarios. |
| `FailedToExecutePlanUseCase` | Agent calls `/callback-shepherd/signal/fail-workflow` during plan execution | Print red error to console and halt — wait for human intervention. See `doc_v2/FailedToExecutePlanUseCaseV2.md` for V2 automated cleanup. |
| `FailedToConvergeUseCase` | Reviewer sends `needs_iteration` beyond `iteration.max` | Present raw reviewer PUBLIC.md + doer PUBLIC.md to user, user decides whether to grant more iterations |

### AgentUnresponsiveUseCase — DetectionContext

The three previously separate unresponsive-agent UseCases (`NoStartupAckUseCase`,
`NoStatusCallbackTimeOutUseCase`, `NoReplyToPingUseCase`) are consolidated into a single
`AgentUnresponsiveUseCase` parameterized by a `DetectionContext` enum. All contexts result
in the same outcome (kill TMUX session, return `AgentCrashed`) with context-specific logging.

| `DetectionContext` | Trigger | Log context | Action |
|---|---|---|---|
| `STARTUP_TIMEOUT` | No callback of any kind within `healthTimeouts.startup` (3 min) after agent spawn (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Session name, HandshakeGuid, env vars, timeout duration | Kill TMUX session. Executor returns `PartResult.AgentCrashed`. Catches startup failures 10x faster than `normalActivity`. |
| `NO_ACTIVITY_TIMEOUT` | `lastActivityTimestamp` stale beyond `healthTimeouts.normalActivity` | Session name, stale duration, `normalActivity` value | Ping agent via TMUX send-keys. (If ping also times out → `PING_TIMEOUT`.) |
| `PING_TIMEOUT` | No callback (`lastActivityTimestamp` unchanged) after `healthTimeouts.pingResponse` window | Session name, ping response duration | Mark as CRASHED, kill TMUX session. Executor completes `signalDeferred` with `AgentSignal.Crashed`, returns `PartResult.AgentCrashed` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts). No automatic recovery in V1. |

**Design rationale**: The three detection contexts share the same conceptual event (agent
is unresponsive) and the same outcome (kill session, return crashed). A single class with
parameterized logging eliminates divergence risk between three classes that should behave
identically, and makes it easy to add new detection triggers (just add an enum value).

**UseCase naming principle**: when logic has a natural UseCase name (verb + noun + context),
encapsulate it in a dedicated UseCase class. These are **simple encapsulated objects** — NOT
a state machine pattern.

---

## FailedToExecutePlanUseCase Detail

When plan execution hits a blocking failure (`FailedWorkflow`, `AgentCrashed`, or
`FailedToConverge` where the user chose to abort):

1. **Print the failure reason in red** to the console — formatted per `PartResult` variant
   (the use case receives the full sealed class, not just a string)
2. **Leave all TMUX sessions alive** — for human inspection. The human can `tmux attach`
   to review agent state, conversation history, or partially completed work.
3. **Block on stdin** — the harness prints a message like
   `"Workflow halted. Press Enter to shut down."` and blocks reading stdin. This keeps the
   process alive (TMUX sessions stay alive as children of the process) without consuming
   resources. The Ctrl+C interrupt protocol (ref.ap.P3po8Obvcjw4IXsSUSU91.E) remains active
   during the block.
4. **On Enter** → harness kills all TMUX sessions
5. **Record failure learning** — `TicketFailureLearningUseCase`
   (ref.ap.cI3odkAZACqDst82HtxKa.E) records structured failure context + LLM summary into
   the ticket's `## Previous Failed Attempts` section. **Non-fatal** — if this step fails
   (LLM error, git error), it logs a warning and continues to exit. The learning is best-effort.
6. **Exit with non-zero exit code**

No automated cleanup, no agent spawning, no git rollback — except for the ticket-failure-learning
step (step 5) which records what happened for the benefit of the next try. The human reviews
the state and decides what to do. V2 will add automated cleanup — see
`doc_v2/FailedToExecutePlanUseCaseV2.md`.

---

## FailedToConvergeUseCase Detail

When the reviewer sends `needs_iteration` but the iteration counter exceeds `iteration.max`:
1. Harness presents the **raw reviewer PUBLIC.md + doer PUBLIC.md** directly to the user, along with the iteration history. No LLM summarization — the user sees the actual, unfiltered agent output.
2. User decides:
   - **Grant more iterations**: user specifies how many additional iterations. `iteration.max` is bumped by that amount. Harness continues the doer→reviewer loop (delivers new instructions via `AckedPayloadSender` — ref.ap.tbtBcVN2iCl1xfHJthllP.E).
   - **Abort**: executor returns `PartResult.FailedToConverge` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts)

Note: `iteration.max` is a **budget**, not a hard limit. The user can override it via `FailedToConvergeUseCase`.

---

## Relationship to Callback Script Retry

Callback scripts retry on transient HTTP failures (ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E) — this is
the **first line of defense** against lost signals. Health monitoring is the **second line of
defense**: it detects when retry was insufficient or the failure is not transient (e.g., agent
process crash, TMUX session death).

Without callback retry, a single transient HTTP failure on `/signal/done` creates a deadlock
that health monitoring cannot resolve — the agent is alive (responds to pings), but the harness
never received the done signal, and the agent has no reason to re-send it. Callback retry
prevents this class of failure from ever reaching the health monitoring layer.

---

## Relationship to Payload Delivery ACK Protocol

The Payload Delivery ACK Protocol (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) addresses a failure mode
that health monitoring alone **cannot** resolve: the "alive but never received instruction"
loop. The ACK protocol catches this at the source (within 9 minutes worst-case) rather than
letting it loop indefinitely through health monitoring. All `send-keys` payloads (except pings)
go through the shared `AckedPayloadSender` abstraction (ref.ap.tbtBcVN2iCl1xfHJthllP.E).

See [Payload Delivery ACK Protocol](../core/agent-to-server-communication-protocol.md#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe)
(ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) for the full specification: wrapping format, retry policy,
ACK tracking, and scope table.

Health monitoring is the **third layer** of delivery assurance — after callback script retry
(ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E) and Payload Delivery ACK. It catches what the first two
layers cannot: agent process crashes, TMUX session death, and truly hung agents.

---

## Edge Case Clarifications

### FailedToConvergeUseCase and Health Loop
When the executor presents the convergence failure to the user and waits for input (inside
`FailedToConvergeUseCase`), the health-aware await loop is **not running**. The executor is
in a synchronous decision path, not in the await loop. No spurious pings or crash declarations
can occur during this user interaction.

### Post-Convergence Doer Session — No Special Ping Needed
After the user grants more iterations, the doer's TMUX session may have been idle for a long
time. The executor sends new instructions directly via `AckedPayloadSender`
(ref.ap.tbtBcVN2iCl1xfHJthllP.E) — **no special-case pre-send ping**. If the session is dead,
`send-keys` will fail or the ACK will never arrive → 3-attempt retry exhausted →
`AgentSignal.Crashed`. The Payload Delivery ACK Protocol
(ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) already handles this exact failure mode generically —
a dedicated ping would duplicate what ACK already does.

### Health Ping During User-Question — Suppressed

When an agent sends a `/user-question`, the server sets `SessionEntry.pendingQA` (non-null),
making `isQAPending == true`. While Q&A is pending, the health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E) **skips all health checks** — no pings, no noActivityTimeout,
no compaction triggers.

**Why suppressed (not "harmless"):** Previously, pings during Q&A were considered harmless
because the agent could respond with `ping-ack`. However, each ping is a TMUX `send-keys`
message that consumes context window capacity. If the human is away for hours (meetings,
walks, overnight), dozens of pings accumulate — pure context-window waste that degrades
the agent's ability to work on the actual task after Q&A completes.

With Q&A-aware suppression, the executor knows the agent is in a **known-idle state** (waiting
for Q&A answer via TMUX). No liveness uncertainty exists — the Q&A coordinator
(ref.ap.NE4puAzULta4xlOLh5kfD.E) owns the session's Q&A lifecycle independently. If the
TMUX session dies during Q&A, the coordinator detects this when `AckedPayloadSender` fails
on answer delivery.

After Q&A completes (all answers batch-delivered, ACK received), the coordinator clears
`pendingQA` → `isQAPending` becomes false → health monitoring and compaction resume normally.
