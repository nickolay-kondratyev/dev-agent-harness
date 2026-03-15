# Agent Health Monitoring — UseCase Pattern / ap.RJWVLgUGjO5zAwupNLhA0.E

Timeout + ping mechanism to detect crashed/hung agents. Each distinct failure scenario
is encapsulated in its own UseCase class — simple, stateless, single-responsibility operations
that the `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) invokes from its health-aware
await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).

---

## Logging Principle

**All health monitoring decisions MUST be logged with structured values.** Every check,
every threshold comparison, every action taken — logged. Health monitoring operates in
the background and its decisions can kill sessions; there must be a full audit trail.

Examples of required log points:
- `"file_updated_timestamp_fresh — treating as proof of life"` with `Val(fileAge, CONTEXT_FILE_AGE)`
- `"file_updated_timestamp_stale — remaining_percentage unreliable"` with `Val(fileAge, CONTEXT_FILE_AGE)`, `Val(threshold, STALENESS_THRESHOLD)`
- `"dual_signal_stale — triggering early ping"` with `Val(fileAge, CONTEXT_FILE_AGE)`, `Val(callbackAge, CALLBACK_AGE)`
- `"ping_suppressed — file_updated_timestamp proves life"` with `Val(fileAge, CONTEXT_FILE_AGE)`
- `"sending_health_ping"` with `Val(sessionName, TMUX_SESSION_NAME)`, `Val(staleDuration, STALE_DURATION)`
- `"crash_detected — no activity after ping"` with `Val(sessionName, TMUX_SESSION_NAME)`

This logging requirement applies to all health monitoring logic — the existing flow steps
below and the dual-signal liveness model.

---

## Flow

0. **Startup acknowledgment** (default: 3 min): After spawn, the executor uses a shorter `noStartupAckTimeout` window. If no callback of any kind (`/callback-shepherd/signal/started`, `/callback-shepherd/signal/done`, etc.) arrives within this window → triggers `NoStartupAckUseCase`. This catches agent startup failures (bad env, binary crash, TMUX issues) in 3 minutes instead of 30. Once any callback arrives, the executor switches to the normal `noActivityTimeout`. See [Agent Startup Acknowledgment](../core/agent-to-server-communication-protocol.md#agent-startup-acknowledgment--apxvsvi2tgooj2eubmoabice) (ref.ap.xVsVi2TgoOJ2eubmoABIC.E).
0b. **Payload delivery ACK** (default: 3 min per attempt, 3 attempts max): After sending instructions via `send-keys`, the executor awaits `/signal/ack-payload` before entering the signal-await loop. If no ACK → retry `send-keys` (up to 2 retries). All retries exhausted → `AgentCrashed`. This catches the "alive but never received instruction" failure mode. See [Payload Delivery ACK Protocol](../core/agent-to-server-communication-protocol.md#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe) (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).
1. **No activity timeout** (default: 30 min): If no activity signal of any kind within configured timeout → triggers `NoStatusCallbackTimeOutUseCase`. Activity is determined by `effectiveLastActivity` — the **more recent** of `SessionEntry.lastActivityTimestamp` (ref.ap.igClEuLMC0bn7mDrK41jQ.E) and `ContextWindowState.fileUpdatedTimestamp` (ref.ap.ufavF1Ztk6vm74dLAgANY.E). See [Dual-Signal Liveness Model](#dual-signal-liveness-model--apdnc1m7qkxvw2zjp8yfre) below.
2. **Ping**: Harness sends a message to agent via TMUX send-keys asking if it's still running and needs more time. Agent is expected to reply via `callback_shepherd.signal.sh ping-ack`
3. **Ping timeout** (default: 3 min): After ping, re-check both `lastActivityTimestamp` and `fileUpdatedTimestamp`. If **any** callback arrived OR `fileUpdatedTimestamp` advanced during the ping window, the agent is alive (proof of life) — loop back to step 1. If **neither signal** shows activity → triggers `NoReplyToPingUseCase`.
4. **Crash handling (V1)**: `NoReplyToPingUseCase` kills TMUX session, completes `signalDeferred` with `AgentSignal.Crashed` → executor returns `PartResult.AgentCrashed` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts — waits for human intervention). **No automatic recovery in V1.** V2 may add retry with `--resume` (ref.ap.LX1GCIjv6LgmM7AJFas20.E).

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
Instead, it re-checks **both** `lastActivityTimestamp` **and** `fileUpdatedTimestamp` after the
ping timeout window. If **any** callback arrived during that window (`user-question`,
`validate-plan`, `done`, `ping-ack`, etc.) **OR** `fileUpdatedTimestamp` advanced (the agent
processed the ping, triggering a conversation turn), the agent is demonstrably alive.
`/ping-ack` exists as a fallback for agents that are alive but idle (no other callbacks to
send) — which is exactly the scenario health monitoring aims to detect.

`fileUpdatedTimestamp` is a **passive** liveness signal — it requires no agent cooperation
beyond the agent being alive and processing tokens. This makes it strictly additive to the
callback-based proof of life.

### Dual-Signal Liveness Model / ap.dnc1m7qKXVw2zJP8yFRE.E

The health monitoring loop uses **two independent liveness signals** to detect agent death —
including the case where the TMUX session is alive but the agent process inside it has exited
(OOM, fatal error, exit to shell prompt).

#### The Two Signals

| Signal | Source | Updated when | Proves |
|--------|--------|-------------|--------|
| `lastActivityTimestamp` | Agent → Server HTTP callbacks | Any callback arrives (started, done, ping-ack, ack-payload, user-question, validate-plan, self-compacted) | Agent can reach the HTTP server and is processing instructions |
| `fileUpdatedTimestamp` | External hook → `context_window_slim.json` (ref.ap.ufavF1Ztk6vm74dLAgANY.E) | After every conversation turn (when the agent stops thinking) | Agent process is alive and consuming/producing tokens |

**`effectiveLastActivity`** = `max(lastActivityTimestamp, fileUpdatedTimestamp)` — the true
"last known alive" time. Used for all `noActivityTimeout` comparisons.

#### Decision Matrix

| `lastActivityTimestamp` | `fileUpdatedTimestamp` | Interpretation | Action |
|---|---|---|---|
| Fresh | Fresh | Agent alive, actively working | No action |
| Stale | **Fresh** | Agent working, no callbacks needed | **Skip ping** — `fileUpdatedTimestamp` is proof of life (log: `ping_suppressed`) |
| **Fresh** | Stale | Agent alive, in long tool execution (e.g., build) | No action — callback proves life |
| Stale | Stale | **Likely dead** or extremely long tool execution | **Early ping** if both stale > `contextFileStaleTimeout` (log: `dual_signal_stale`) |

#### Ping Suppression

When `fileUpdatedTimestamp` is recent (within `contextFileStaleTimeout` — default 5 min),
the agent is **provably alive** even if `lastActivityTimestamp` is old. The executor skips
the ping and resets the health timer. This prevents unnecessary interruptions to agents on
long tasks that involve many API turns but no harness callbacks.

#### Early Ping — In-Session Agent Death Detection

When **both** signals are stale beyond `contextFileStaleTimeout`, the harness suspects
in-session agent death and sends an **early ping** — without waiting for the full
`noActivityTimeout` (30 min).

```
if (now() - fileUpdatedTimestamp > contextFileStaleTimeout
    AND now() - lastActivityTimestamp > contextFileStaleTimeout) {
    // Both signals stale — send early ping (log: dual_signal_stale)
    sendPing()
    // Standard pingTimeout (3 min) applies
    // After ping: check BOTH signals for any advancement
    // Neither advanced → NoReplyToPingUseCase → Crashed
}
```

**Detection time improvement**: For the common case where the agent dies mid-work after ACKing:
- **Before**: ~33 min (30 min `noActivityTimeout` + 3 min `pingTimeout`)
- **After**: ~8 min (5 min `contextFileStaleTimeout` + 3 min `pingTimeout`)

#### Stale Context Guard

When `fileUpdatedTimestamp` is stale, `remaining_percentage` from `context_window_slim.json`
is **unreliable** — the agent may have died and the percentage is frozen. The harness skips
self-compaction threshold checks when the context state is stale and falls through to the
dual-signal liveness check. See `ContextWindowSelfCompactionUseCase`
(ref.ap.8nwz2AHf503xwq8fKuLcl.E) for the integration point.

#### Post-Ping Proof of Life — Dual Check

After sending a ping (whether early or standard), the executor checks **both** signals:

```
afterPingWindow:
    callbackAdvanced = sessionEntry.lastActivityTimestamp > prePingTimestamp
    fileAdvanced = contextState.fileUpdatedTimestamp > prePingFileTimestamp

    if (callbackAdvanced OR fileAdvanced) {
        // Agent is alive (proof of life via either signal)
        // Log which signal proved life
        continue  // loop back to normal monitoring
    }
    // Neither signal advanced → agent is dead
    NoReplyToPingUseCase.execute(sessionEntry)
```

### Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `noStartupAckTimeout` | 3 min | Time after spawn before declaring startup failure — catches misconfigured env, binary crashes. Applies only until the first callback arrives, then switches to `noActivityTimeout`. |
| `healthCheckInterval` | 5 min | How often the executor checks `lastActivityTimestamp` while awaiting the deferred |
| `noActivityTimeout` | 30 min | Elapsed time since `effectiveLastActivity` before triggering a ping. **Configurable per sub-part** — see below. |
| `pingTimeout` | 3 min | Time to wait after ping before declaring crash (any activity in either signal resets) |
| `contextFileStaleTimeout` | 5 min | When **both** `lastActivityTimestamp` and `fileUpdatedTimestamp` are stale beyond this threshold, send an early ping. Significantly shorter than `noActivityTimeout` because dual-signal staleness is a strong indicator of agent death. |

### Per-Sub-Part noActivityTimeout Override

The default 30-minute `noActivityTimeout` is appropriate for most agents, but some roles have
different expected work durations:

- A **planning agent** may legitimately work for 25+ minutes on complex codebases
- A **reviewer** typically finishes much faster

To avoid one-size-fits-all, `noActivityTimeout` can be **overridden per sub-part** via an
optional `noActivityTimeoutMinutes` field in the sub-part config (plan.json / workflow JSON).
When present, it replaces the global default for that sub-part's health monitoring.

| Source | How set |
|--------|---------|
| `plan.json` (with-planning) | Planner assigns per sub-part based on expected work duration |
| `config/workflows/*.json` (straightforward) | Operator specifies in the static workflow JSON |
| Global default | 30 min — used when the field is absent |

The `PartExecutor` reads this value from the sub-part config in `current_state.json` when
initializing its health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).

### What Runs Where

| Concern | Owner | Mechanism |
|---------|-------|-----------|
| Update `lastActivityTimestamp` | `ShepherdServer` | On every incoming callback (including `/started`) |
| Read `fileUpdatedTimestamp` | `PartExecutor` | Via `AgentFacade.readContextWindowState()` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) on every 1-second poll iteration |
| Compute `effectiveLastActivity` | `PartExecutor` | `max(lastActivityTimestamp, fileUpdatedTimestamp)` — the true "last known alive" time |
| Check startup ack timeout | `PartExecutor` | Health-aware await loop — uses `noStartupAckTimeout` until first callback arrives |
| Check dual-signal staleness, trigger early ping | `PartExecutor` | Health-aware await loop — both signals stale > `contextFileStaleTimeout` → early ping (ref.ap.dnc1m7qKXVw2zJP8yFRE.E) |
| Check `effectiveLastActivity` staleness, trigger standard ping | `PartExecutor` | Health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) — `effectiveLastActivity` stale > `noActivityTimeout` |
| Suppress unnecessary ping | `PartExecutor` | If `fileUpdatedTimestamp` is fresh, skip ping even when `lastActivityTimestamp` is stale |
| Send ping message via TMUX | `PartExecutor` | Via `AgentFacade.sendHealthPing()` — delegates internally to `NoStatusCallbackTimeOutUseCase` |
| Post-ping dual check | `PartExecutor` | After ping timeout: check both `lastActivityTimestamp` and `fileUpdatedTimestamp` for advancement |
| Declare crash, kill TMUX | `PartExecutor` | Via `AgentFacade.killSession()` — delegates internally to `NoReplyToPingUseCase` |
| Complete deferred with `Crashed` | `PartExecutor` | After kill session executes |
| Complete deferred with `Done`/`FailWorkflow` | `ShepherdServer` | On `/done` or `/fail-workflow` callback (via `SessionsState` internal to `AgentFacadeImpl`) |

### Testability

The health monitoring logic is the most timing-sensitive code in the system. It is unit-tested
via `FakeAgentFacade` + virtual time (`TestClock` + `kotlinx-coroutines-test`):

- `FakeAgentFacade.readContextWindowState()` returns programmable `ContextWindowState`
  (any `remaining_percentage`, any `fileUpdatedTimestamp`)
- `TestClock` (ref.ap.whDS8M5aD2iggmIjDIgV9.E) controls `now()` for timestamp age comparisons
- `advanceTimeBy()` controls coroutine delays (1-second ticks, timeout windows)
- Tests verify every decision branch: ping suppression, early ping, standard ping, crash
  declaration, proof-of-life acceptance

See [Testing Strategy](../high-level.md#testing-strategy--fake-driven-unit-coverage) in
high-level.md for the full testing approach.

---

## UseCase Classes

| UseCase | Trigger | Action |
|---|---|---|
| `NoStartupAckUseCase` | No callback of any kind within `noStartupAckTimeout` (3 min) after agent spawn (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Log clear error identifying spawn failure (includes session name, HandshakeGuid, env vars). Kill TMUX session. Executor returns `PartResult.AgentCrashed`. Catches startup failures 10x faster than `noActivityTimeout`. |
| `NoStatusCallbackTimeOutUseCase` | `effectiveLastActivity` (`max(lastActivityTimestamp, fileUpdatedTimestamp)`) stale beyond `noActivityTimeout`, OR both signals stale beyond `contextFileStaleTimeout` (early ping — ref.ap.dnc1m7qKXVw2zJP8yFRE.E) | Ping agent via TMUX send-keys |
| `NoReplyToPingUseCase` | No advancement in **either** signal (`lastActivityTimestamp` or `fileUpdatedTimestamp`) after ping timeout (ref.ap.dnc1m7qKXVw2zJP8yFRE.E) | Mark as CRASHED, kill TMUX session. Executor completes `signalDeferred` with `AgentSignal.Crashed`, returns `PartResult.AgentCrashed` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts). No automatic recovery in V1. |
| `FailedToExecutePlanUseCase` | Agent calls `/callback-shepherd/signal/fail-workflow` during plan execution | Print red error to console and halt — wait for human intervention. See `doc_v2/FailedToExecutePlanUseCaseV2.md` for V2 automated cleanup. |
| `FailedToConvergeUseCase` | Reviewer sends `needs_iteration` beyond `iteration.max` | Summarize state via BudgetHigh DirectLLM (ref.ap.hnbdrLkRtNSDFArDFd9I2.E), present to user, user decides whether to grant more iterations |

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
1. Harness uses **BudgetHigh DirectLLM** (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) to summarize the current state (reviewer's PUBLIC.md + doer's PUBLIC.md + SHARED_CONTEXT.md)
2. Presents summary to user with the iteration history
3. User decides:
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

### Health Ping During User-Question
When an agent sends a `/user-question` and the human is thinking, `lastActivityTimestamp` was
reset when the question callback arrived. If the human takes longer than `noActivityTimeout`
to respond, the health loop would normally send a ping. However, with the dual-signal model,
the agent's `fileUpdatedTimestamp` may still be recent (the agent is alive, waiting for input),
which suppresses the ping. If both signals do go stale (very long human think time), a ping
is sent — this is **harmless**: the agent can respond with `ping-ack` while waiting for the
Q&A answer delivery via TMUX `send-keys`.
