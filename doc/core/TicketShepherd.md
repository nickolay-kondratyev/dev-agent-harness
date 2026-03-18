# TicketShepherd / ap.P3po8Obvcjw4IXsSUSU91.E

The central coordinator that drives a ticket through its entire workflow lifecycle.
`TicketShepherd` is the one moving ticket through the motions — it sets up the plan,
creates executors for each part, runs them in sequence, and handles the results.

## How It Drives the Workflow

1. Initial setup (`current_state.json` initialization — branch already created by
   `TicketShepherdCreator` ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
2. `SetupPlanUseCase.setup()` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) → `List<Part>`.
   Returns execution-ready parts regardless of workflow mode. For `with-planning`
   workflows, the planning lifecycle (executor, plan conversion, retry on validation
   failure) is fully handled by `DetailedPlanningUseCase`
   (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) internally — `TicketShepherd` is not involved.
3. For each execution Part:
   a. Create `PartExecutorImpl` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E):
      - 2 sub-parts → `PartExecutorImpl(reviewerConfig = reviewerSubPart)`
      - 1 sub-part → `PartExecutorImpl(reviewerConfig = null)`
   b. `activeExecutor` = executor
   c. `executor.execute()` → `PartResult`
   d. Handle `PartResult`:
      - `Completed` → kill TMUX sessions for part, move to next part
      - `FailedWorkflow` → delegate to `FailedToExecutePlanUseCase(partResult)` (prints red error to
        console, halts — waits for human intervention).
      - `FailedToConverge` → delegate to `FailedToExecutePlanUseCase(partResult)` (user already chose to abort inside executor's `FailedToConvergeUseCase` call)
      - `AgentCrashed` → delegate to `FailedToExecutePlanUseCase(partResult)` (prints red error to
        console, halts — waits for human intervention). V1: no automatic recovery.

`FailedToExecutePlanUseCase` receives a `PartResult` sealed class carrying enough context
for formatted error messages and gives V2 the type information needed for different
cleanup strategies. Planning failures (executor failures, plan conversion failures) are
handled internally by `DetailedPlanningUseCase` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E).
4. On all parts completed — **workflow success**:
   a. **Final commit** — `GitCommitStrategy.onSubPartDone` was already called for the last
      sub-part, but the shepherd performs one final `git add -A && git commit` to capture
      any remaining state (e.g., final `current_state.json` updates). Skipped if working
      tree is clean (no changes since last commit).
   b. **Update ticket status** — set ticket `status` to `done` in the ticket file's YAML
      frontmatter.
   c. **Kill all TMUX sessions** — defensive cleanup. All parts already called
      `removeAllForPart`, so normally none remain. This catches any leaked sessions.
   d. **Print success message in green** — e.g., `"Workflow completed successfully for
      ticket {TICKET_ID}."`
   e. **Exit code 0**
   f. **Does NOT push the branch** — pushing is the caller's responsibility, same as
      marking the ticket `in_progress` before `shepherd run`.

## Fields

| Field | Type | Purpose |
|-------|------|---------|
| `activeExecutor` | `PartExecutor?` | The currently running executor. Single reference point for cancellation. `null` between parts. Health monitoring is internal to each executor (ref.ap.QCjutDexa2UBDaKB3jTcF.E). |
| `originatingBranch` | `String` | The branch from which the try branch was created. Recorded by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) before branch creation. Used by `TicketFailureLearningUseCase` (ref.ap.cI3odkAZACqDst82HtxKa.E) for harness-owned best-effort propagation of failure learning to the originating branch. |
| `tryNumber` | `Int` | The try number for this run (e.g., 1 for try-1). Resolved by `TicketShepherdCreator`. Used by `TicketFailureLearningUseCase` for the `### TRY-{N}` section heading. |

## Responsibilities

- **Provides `AgentFacade`** (ref.ap.9h0KS4EOK5yumssRCJdbq.E) to executors — the
  testability facade through which all agent operations flow. `SessionsState`
  (ref.ap.7V6upjt21tOoCFXA7nqNh.E) is internal to `AgentFacadeImpl`; the shepherd
  passes the `AgentFacade` interface to each `PartExecutor` it creates, not the raw
  infra components.
- **Orchestrates use cases** — calls use cases for discrete operations; the shepherd makes
  the decisions, use cases do the work.
- **Delegates iteration to PartExecutor** — does NOT drive the doer↔reviewer loop directly.
  Creates an appropriate `PartExecutor` for each part and calls `execute()`. The executor
  owns the spawn → wait → iterate cycle internally.
- **Manages part lifecycle** — creates executors, kills TMUX sessions when a part completes
  (via `AgentFacade`). Git commits are handled by the executor via `GitCommitStrategy.onSubPartDone` (see [Git Commit Strategy](git.md) ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).
- **Controls which executor is active** — always holds a single `activeExecutor` reference,
  giving cancellation one place to look. Health monitoring is owned by each executor
  internally via its health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).

## What TicketShepherd Does NOT Do

- Does **not** interpret `/callback-shepherd/signal/done` results directly — the server completes
  the `signalDeferred` on `SessionEntry`, and the executor reads the `AgentSignal`.
- Does **not** assemble agent instructions — delegates to `ContextForAgentProvider`
  (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) via the executor.
- Does **not** run or orchestrate the planning phase — `SetupPlanUseCase`
  (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) returns execution-ready `List<Part>`;
  `DetailedPlanningUseCase` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) owns the full
  planning lifecycle internally.

## Dependencies

Receives `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E — defined in code at
`ShepherdContext.kt`) for shared infrastructure (tmux, LLM, use cases) plus ticket-scoped
state (`SessionsState`, parsed workflow, ticket metadata) wired by
`TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E).

Additional dependencies:
- `TicketFailureLearningUseCase` (ref.ap.cI3odkAZACqDst82HtxKa.E) — records structured failure
  context into the ticket on workflow failure, invoked by `FailedToExecutePlanUseCase` before halting.

## Interrupt Protocol (Ctrl+C)

The harness uses the standard **double-Ctrl+C pattern** to prevent accidental termination:

1. First Ctrl+C → harness prints `"Press Ctrl+C again to confirm exit."` and records the
   current timestamp. Execution continues uninterrupted.
2. Second Ctrl+C within **2 seconds** of the first → harness kills all TMUX sessions, writes
   `current_state.json` with `FAILED` status on active sub-parts, and exits with non-zero
   code.
3. Second Ctrl+C after **more than 2 seconds** → treated as a fresh first Ctrl+C (timestamp
   resets, prompt reprints).

Why this pattern over stdin confirmation:
- **No stdin contention** — avoids conflict with `StdinUserQuestionHandler` which also reads
  stdin for agent Q&A.
- **No blocking window** — does not hold a 10-second stdin read that delays signal delivery
  or ties up the input stream.
- **Standard CLI idiom** — users already know the double-Ctrl+C convention; no prompt
  interaction required.

---

## Not a Use Case

`TicketShepherd` is a long-lived coordinator, not a discrete operation. It lives for the
duration of a ticket's processing and holds mutable state (`SessionsState`). Use cases are
stateless operations it delegates to.
