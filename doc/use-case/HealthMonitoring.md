# Agent Health Monitoring — UseCase Pattern / ap.RJWVLgUGjO5zAwupNLhA0.E

Timeout + ping mechanism to detect crashed/hung agents. Each distinct failure scenario
is encapsulated in its own UseCase class — simple, stateless, single-responsibility operations
that the `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) invokes from its health-aware
await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).

---

## Flow

0. **Startup acknowledgment** (default: 3 min): After spawn, the executor uses a shorter `noStartupAckTimeout` window. If no callback of any kind (`/callback-shepherd/signal/started`, `/callback-shepherd/signal/done`, etc.) arrives within this window → triggers `NoStartupAckUseCase`. This catches agent startup failures (bad env, binary crash, TMUX issues) in 3 minutes instead of 30. Once any callback arrives, the executor switches to the normal `noActivityTimeout`. See [Agent Startup Acknowledgment](../core/agent-to-server-communication-protocol.md#agent-startup-acknowledgment--apxvsvi2tgooj2eubmoabice) (ref.ap.xVsVi2TgoOJ2eubmoABIC.E).
1. **No activity timeout** (default: 30 min): If no callback of any kind (signal or query) within configured timeout → triggers `NoStatusCallbackTimeOutUseCase`. Uses `SessionEntry.lastActivityTimestamp` (ref.ap.igClEuLMC0bn7mDrK41jQ.E) — any callback resets the timer.
2. **Ping**: Harness sends a message to agent via TMUX send-keys asking if it's still running and needs more time. Agent is expected to reply via `callback_shepherd.signal.sh ping-ack`
3. **Ping timeout** (default: 3 min): After ping, re-check `lastActivityTimestamp`. If **any** callback arrived during the ping window, the agent is alive (proof of life) — loop back to step 1. If **no** activity at all → triggers `NoReplyToPingUseCase`.
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
Instead, it re-checks `lastActivityTimestamp` after the ping timeout window. If **any**
callback arrived during that window (`user-question`, `validate-plan`, `done`, `ping-ack`,
etc.), the agent is demonstrably alive. `/ping-ack` exists as a fallback for agents that are
alive but idle (no other callbacks to send) — which is exactly the scenario health monitoring
aims to detect.

### Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `noStartupAckTimeout` | 3 min | Time after spawn before declaring startup failure — catches misconfigured env, binary crashes. Applies only until the first callback arrives, then switches to `noActivityTimeout`. |
| `healthCheckInterval` | 5 min | How often the executor checks `lastActivityTimestamp` while awaiting the deferred |
| `noActivityTimeout` | 30 min | Elapsed time since `lastActivityTimestamp` before triggering a ping |
| `pingTimeout` | 3 min | Time to wait after ping before declaring crash (any activity resets) |

### What Runs Where

| Concern | Owner | Mechanism |
|---------|-------|-----------|
| Update `lastActivityTimestamp` | `ShepherdServer` | On every incoming callback (including `/started`) |
| Check startup ack timeout | `PartExecutor` | Health-aware await loop — uses `noStartupAckTimeout` until first callback arrives |
| Check staleness, trigger ping | `PartExecutor` | Health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) |
| Send ping message via TMUX | `NoStatusCallbackTimeOutUseCase` | Called by executor when activity is stale |
| Declare crash, kill TMUX | `NoReplyToPingUseCase` | Called by executor when ping window expires with no activity |
| Complete deferred with `Crashed` | `PartExecutor` | After `NoReplyToPingUseCase` executes |
| Complete deferred with `Done`/`FailWorkflow` | `ShepherdServer` | On `/done` or `/fail-workflow` callback |

---

## UseCase Classes

| UseCase | Trigger | Action |
|---|---|---|
| `NoStartupAckUseCase` | No callback of any kind within `noStartupAckTimeout` (3 min) after agent spawn (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Log clear error identifying spawn failure (includes session name, HandshakeGuid, env vars). Kill TMUX session. Executor returns `PartResult.AgentCrashed`. Catches startup failures 10x faster than `noActivityTimeout`. |
| `NoStatusCallbackTimeOutUseCase` | No activity (any callback) after X min — based on `lastActivityTimestamp` | Ping agent via TMUX send-keys |
| `NoReplyToPingUseCase` | No activity of any kind (proof-of-life check on `lastActivityTimestamp`) after ping timeout | Mark as CRASHED, kill TMUX session. Executor completes `signalDeferred` with `AgentSignal.Crashed`, returns `PartResult.AgentCrashed` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts). No automatic recovery in V1. |
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
   `"Workflow halted. Press Enter to shut down, or Ctrl+C to use the interrupt protocol."`
   and blocks reading stdin. This keeps the process alive (TMUX sessions stay alive as
   children of the process) without consuming resources.
4. **On Enter** → harness kills all TMUX sessions
5. **Record failure learning** — `TicketFailureLearningUseCase`
   (ref.ap.cI3odkAZACqDst82HtxKa.E) records structured failure context + LLM summary into
   the ticket's `## Previous Failed Attempts` section. **Non-fatal** — if this step fails
   (LLM error, git error), it logs a warning and continues to exit. The learning is best-effort.
6. **Exit with non-zero exit code**
7. **Ctrl+C interrupt protocol still applies** — the two-layer flow
   (ref.ap.P3po8Obvcjw4IXsSUSU91.E) works during the stdin block, giving the human the
   option to interrupt agents or kill sessions selectively before the full shutdown.

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
   - **Grant more iterations**: user specifies how many additional iterations. `iteration.max` is bumped by that amount. Harness continues the doer→reviewer loop (sends new instructions via TMUX `send-keys`).
   - **Abort**: executor returns `PartResult.FailedToConverge` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts)

Note: `iteration.max` is a **budget**, not a hard limit. The user can override it via `FailedToConvergeUseCase`.

---

## Edge Case Clarifications

### FailedToConvergeUseCase and Health Loop
When the executor presents the convergence failure to the user and waits for input (inside
`FailedToConvergeUseCase`), the health-aware await loop is **not running**. The executor is
in a synchronous decision path, not in the await loop. No spurious pings or crash declarations
can occur during this user interaction.

### Post-Convergence Doer Session Check
After the user grants more iterations, the doer's TMUX session may have been idle for a long
time. Before sending new instructions, the executor **pings the doer session first** to confirm
it is still alive. If the doer responds (any callback including ping-ack), the executor proceeds
with new instructions. If the doer is dead, the executor triggers crash handling.

### Health Ping During User-Question
When an agent sends a `/user-question` and the human is thinking, `lastActivityTimestamp` was
reset when the question callback arrived. If the human takes longer than `noActivityTimeout`
to respond, the health loop will send a ping. This is **harmless** — the agent can respond
with `ping-ack` while waiting for the Q&A answer delivery via TMUX `send-keys`.
