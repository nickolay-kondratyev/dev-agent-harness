# Agent Health Monitoring — UseCase Pattern / ap.RJWVLgUGjO5zAwupNLhA0.E

Timeout + ping mechanism to detect crashed/hung agents. Each distinct failure scenario
is encapsulated in its own UseCase class — simple, stateless, single-responsibility operations
that `TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E) delegates to.

---

## Flow

1. **No activity timeout** (default: 30 min): If no callback of any kind (`/callback-shepherd/done`, `/callback-shepherd/fail-workflow`, `/callback-shepherd/user-question`, or `/callback-shepherd/ping-ack`) within configured timeout → triggers `NoStatusCallbackTimeOutUseCase`. Uses `SessionEntry.lastActivityTimestamp` (ref.ap.igClEuLMC0bn7mDrK41jQ.E) — any callback resets the timer.
2. **Ping**: Harness sends a message to agent via TMUX send-keys asking if it's still running and needs more time. Agent is expected to reply via `callback_shepherd.ping-ack.sh`
3. **Ping timeout** (default: 3 min): If no `/callback-shepherd/ping-ack` reply → triggers `NoReplyToPingUseCase`
4. **Crash handling**: `NoReplyToPingUseCase` kills TMUX session, completes `signalDeferred` with `AgentSignal.Crashed` → executor returns `PartResult.AgentCrashed` → `TicketShepherd` creates a new executor for the same part (no `--resume` in V1 — ref.ap.LX1GCIjv6LgmM7AJFas20.E)

---

## UseCase Classes

| UseCase | Trigger | Action |
|---|---|---|
| `NoStatusCallbackTimeOutUseCase` | No activity (any callback) after X min — based on `lastActivityTimestamp` | Ping agent via TMUX send-keys |
| `NoReplyToPingUseCase` | No `/callback-shepherd/ping-ack` reply after Y min | Mark as CRASHED, kill TMUX session, complete `signalDeferred` with `AgentSignal.Crashed`. Executor returns `PartResult.AgentCrashed` → `TicketShepherd` handles recovery (new executor for same part) or abort. |
| `FailedToExecutePlanUseCase` | Agent calls `/callback-shepherd/fail-workflow` during plan execution | Print red error to console and halt — wait for human intervention. See `doc_v2/FailedToExecutePlanUseCaseV2.md` for V2 automated cleanup. |
| `FailedToConvergeUseCase` | Reviewer sends `needs_iteration` beyond `iteration.max` | Summarize state via BudgetHigh DirectLLM (ref.ap.hnbdrLkRtNSDFArDFd9I2.E), present to user, user decides whether to grant more iterations |

**UseCase naming principle**: when logic has a natural UseCase name (verb + noun + context),
encapsulate it in a dedicated UseCase class. These are **simple encapsulated objects** — NOT
a state machine pattern.

---

## FailedToExecutePlanUseCase Detail

When plan execution hits blocking issues (agent calls `/callback-shepherd/fail-workflow`):

1. **Print the failure reason in red** to the console
2. **Halt** the harness process
3. Wait for human intervention

No automated cleanup, no agent spawning, no git rollback. The human reviews the state and
decides what to do. V2 will add automated cleanup — see `doc_v2/FailedToExecutePlanUseCaseV2.md`.

---

## FailedToConvergeUseCase Detail

When the reviewer sends `needs_iteration` but the iteration counter exceeds `iteration.max`:
1. Harness uses **BudgetHigh DirectLLM** (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) to summarize the current state (reviewer's PUBLIC.md + doer's PUBLIC.md + SHARED_CONTEXT.md)
2. Presents summary to user with the iteration history
3. User decides:
   - **Grant more iterations**: user specifies how many additional iterations. `iteration.max` is bumped by that amount. Harness continues the doer→reviewer loop (sends new instructions via TMUX `send-keys`).
   - **Abort**: executor returns `PartResult.FailedToConverge` → `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (prints red error, halts)

Note: `iteration.max` is a **budget**, not a hard limit. The user can override it via `FailedToConvergeUseCase`.
