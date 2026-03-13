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
      ref.ap.mxIc5IOj6qYI7vgLcpQn5.E for PLANNER↔PLAN_REVIEWER).
      Planning sub-parts use the constant `partName = "planning"` for `SessionsState`
      registration, `removeAllForPart` cleanup, and commit messages
      (e.g., `[shepherd] planning/plan — completed`). This is a synthetic name — it does
      not come from `plan.json` (which doesn't exist yet during planning).
   b. `planningExecutor.execute()` → `PartResult`
   c. Handle `PartResult` — **same logic as execution parts** (step 4d):
      - `Completed` → proceed to 3d
      - Any failure (`FailedWorkflow`, `FailedToConverge`, `AgentCrashed`) →
        delegate to `FailedToExecutePlanUseCase(partResult)` (red error, halt).
        Planning failures are rare (plan validated via `/callback-shepherd/validate-plan`
        ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E before agents signal done). When they happen,
        the human is the right handler — no special recovery logic.
   d. Kill TMUX sessions for the planning part (`removeAllForPart`), `GitCommitStrategy.onPartDone`
   e. `convertPlanToExecutionParts()` — `plan.json` → `current_state.json` → `List<Part>`.
      Throws `PlanConversionException` on malformed/invalid plan; `TicketShepherd` catches it
      and delegates to `FailedToExecutePlanUseCase(planConversionException)`.
4. For each execution Part:
   a. Create `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E):
      - 2 sub-parts → `DoerReviewerPartExecutor`
      - 1 sub-part → `SingleDoerPartExecutor`
   b. `activeExecutor` = executor
   c. `executor.execute()` → `PartResult`
   d. Handle `PartResult`:
      - `Completed` → kill TMUX sessions for part, `GitCommitStrategy.onPartDone` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E), move to next part
      - `FailedWorkflow` → delegate to `FailedToExecutePlanUseCase(partResult)` (prints red error to
        console, halts — waits for human intervention).
      - `FailedToConverge` → delegate to `FailedToExecutePlanUseCase(partResult)` (user already chose to abort inside executor's `FailedToConvergeUseCase` call)
      - `AgentCrashed` → delegate to `FailedToExecutePlanUseCase(partResult)` (prints red error to
        console, halts — waits for human intervention). V1: no automatic recovery.

`FailedToExecutePlanUseCase` receives either a `PartResult` sealed class (for execution and
planning executor failures) or a `PlanConversionException` (for plan conversion failures in
step 3e). Both carry enough context for formatted error messages and give V2 the type
information needed for different cleanup strategies.
5. On all parts completed — **workflow success**:
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
| `originatingBranch` | `String` | The branch from which the try branch was created. Recorded by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) before branch creation. Used by `TicketFailureLearningUseCase` (ref.ap.cI3odkAZACqDst82HtxKa.E) for failure-learning propagation. |
| `tryNumber` | `Int` | The try number for this run (e.g., 1 for try-1). Resolved by `TicketShepherdCreator`. Used by `TicketFailureLearningUseCase` for the `### TRY-{N}` section heading. |

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
- **Controls which executor is active** — always holds a single `activeExecutor` reference,
  giving cancellation one place to look. Health monitoring is owned by each executor
  internally via its health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).

## What TicketShepherd Does NOT Do

- Does **not** interpret `/callback-shepherd/done` results directly — the server completes
  the `signalDeferred` on `SessionEntry`, and the executor reads the `AgentSignal`.
- Does **not** assemble agent instructions — delegates to `SubPartInstructionProvider`
  (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) via the executor.
- Does **not** run the planning iteration loop itself — delegates to a planning
  `DoerReviewerPartExecutor` created by `DetailedPlanningUseCase`
  (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E).

## Dependencies

Receives `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E — defined in code at
`ShepherdContext.kt`) for shared infrastructure (tmux, LLM, use cases) plus ticket-scoped
state (`SessionsState`, parsed workflow, ticket metadata) wired by
`TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E).

Additional dependencies:
- `TicketFailureLearningUseCase` (ref.ap.cI3odkAZACqDst82HtxKa.E) — records structured failure
  context into the ticket on workflow failure, invoked by `FailedToExecutePlanUseCase` before halting.

## Interrupt Protocol (Ctrl+C)

When the user presses Ctrl+C, the harness intercepts the signal via a JVM shutdown hook and
enters a **two-layer confirmation flow** instead of killing immediately:

### Layer 1: Interrupt Agents

1. Harness prints: `"Ctrl+C received. Type 'int' + Enter to send interrupt to all agent sessions, or wait 10s to resume."`
2. If user types `int` + Enter → harness sends SIGINT to all active TMUX agent sessions (agents stop, sessions stay alive). Prints status: `"Interrupt sent to N agent session(s). Sessions are still alive."`
3. If user types anything else or 10 seconds elapse → harness prints `"Resuming..."` and continues execution as if nothing happened.

### Layer 2: Kill Sessions

After Layer 1 interrupt is sent:

1. Harness prints: `"Type 'kill' + Enter to destroy all TMUX sessions and exit, or wait 10s to resume."`
2. If user types `kill` + Enter → harness kills all TMUX sessions, writes `current_state.json` with `FAILED` status on active sub-parts, and exits.
3. If user types anything else or 10 seconds elapse → harness prints `"Resuming..."` and continues execution.

This prevents accidental workflow termination from a stray Ctrl+C.

---

## Not a Use Case

`TicketShepherd` is a long-lived coordinator, not a discrete operation. It lives for the
duration of a ticket's processing and holds mutable state (`SessionsState`). Use cases are
stateless operations it delegates to.
