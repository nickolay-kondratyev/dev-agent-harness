# TicketShepherd / ap.P3po8Obvcjw4IXsSUSU91.E

The central coordinator that drives a ticket through its entire workflow lifecycle.
`TicketShepherd` is the one moving ticket through the motions — it sets up the plan,
creates executors for each part, runs them in sequence, and handles the results.

## How It Drives the Workflow

1. Initial setup (`current_state.json` initialization — branch already created by
   `TicketShepherdCreator` ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
2. `SetupPlanUseCase` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) → `SetupPlanResult`
   (ref.ap.evYmpQfliHCHUTdK2QRgS.E)
3. If `SetupPlanResult.NeedsPlanning`:
   a. `activeExecutor` = planning executor (a `DoerReviewerPartExecutor`
      ref.ap.mxIc5IOj6qYI7vgLcpQn5.E for PLANNER↔PLAN_REVIEWER)
   b. `planningExecutor.execute()` — runs the planning iteration loop
   c. `convertPlanToExecutionParts()` — `plan.json` → `current_state.json` → `List<Part>`
4. For each execution Part:
   a. Create `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E):
      - 2 sub-parts → `DoerReviewerPartExecutor`
      - 1 sub-part → `SingleDoerPartExecutor`
   b. `activeExecutor` = executor
   c. `executor.execute()` → `PartResult`
   d. Handle `PartResult`:
      - `Completed` → kill TMUX sessions for part, `GitCommitStrategy.onPartDone` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E), move to next part
      - `FailedWorkflow` → delegate to `FailedToExecutePlanUseCase` (prints red error to
        console, halts — waits for human intervention).
      - `FailedToConverge` → delegate to `FailedToExecutePlanUseCase` (user already chose to abort inside executor's `FailedToConvergeUseCase` call)
      - `AgentCrashed` → attempt recovery or abort
5. On all parts completed → workflow done

## Fields

| Field | Type | Purpose |
|-------|------|---------|
| `activeExecutor` | `PartExecutor?` | The currently running executor. Single reference point for health monitoring and cancellation. `null` between parts. |

## Responsibilities

- **Owns `SessionsState`** (ref.ap.7V6upjt21tOoCFXA7nqNh.E) — tracks live `TmuxAgentSession`
  (ref.ap.DAwDPidjM0HMClPDSldXt.E) instances, keyed by HandshakeGuid.
- **Orchestrates use cases** — calls use cases for discrete operations; the shepherd makes
  the decisions, use cases do the work.
- **Delegates iteration to PartExecutor** — does NOT drive the doer↔reviewer loop directly.
  Creates an appropriate `PartExecutor` for each part and calls `execute()`. The executor
  owns the spawn → wait → iterate cycle internally.
- **Manages part lifecycle** — creates executors, kills TMUX sessions when a part completes
  (`removeAllForPart`), triggers `GitCommitStrategy` hooks (see [Git Commit Strategy](git.md) ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).
- **Monitors agent health** — triggers `NoStatusCallbackTimeOutUseCase` and
  `NoReplyToPingUseCase` when agents go silent. The `activeExecutor` reference tells the
  health monitor which executor's deferred to complete with `AgentSignal.Crashed`
  (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E).
- **Controls which executor is active** — always holds a single `activeExecutor` reference,
  giving health monitoring and cancellation one place to look.

## What TicketShepherd Does NOT Do

- Does **not** interpret `/callback-shepherd/done` results directly — the server completes
  the `signalDeferred` on `SessionEntry`, and the executor reads the `AgentSignal`.
- Does **not** assemble agent instructions — delegates to `SubPartInstructionProvider`
  (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) via the executor.
- Does **not** run the planning iteration loop itself — delegates to a planning
  `DoerReviewerPartExecutor` created by `DetailedPlanningUseCase`
  (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E).

## Dependencies

Receives `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E) for shared infrastructure
(tmux, LLM, use cases) plus ticket-scoped state (`SessionsState`, parsed workflow, ticket
metadata) wired by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E).

## Not a Use Case

`TicketShepherd` is a long-lived coordinator, not a discrete operation. It lives for the
duration of a ticket's processing and holds mutable state (`SessionsState`). Use cases are
stateless operations it delegates to.
