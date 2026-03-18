#need-tickets
# TicketShepherdCreator / ap.cJbeC4udcM3J8UFoWXfGh.E

Creates a fully wired `TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E) for a single run.

## Responsibility

Receives `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E) plus ticket-specific inputs
(ticket path, workflow name), resolves ticket-scoped dependencies, and returns a ready-to-go
`TicketShepherd`. One shepherd per run — the creator is called once from the CLI entry point.

## Inputs

- `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E — defined in code at
  `ShepherdContext.kt`) — shared infrastructure (tmux, logging, use cases). Already
  initialized by `ContextInitializer` (ref.ap.9zump9YISPSIcdnxEXZZX.E — defined in code at
  `ContextInitializer.kt`) before the creator runs.
- Ticket path — the markdown file to process
- Workflow name — which workflow JSON to load

## What It Does

- Resolves workflow JSON from `config/workflows/<name>.json`
- Parses the ticket (frontmatter `id`, `title`, `status`). **Fails hard** if any of these
  required fields are missing or empty — prints a clear error naming the missing field(s)
  and the ticket path.
- **Validates ticket status** — `status` must be `in_progress`. Fail hard with clear error
  if not. Marking the ticket as `in_progress` and pushing to remote is the caller's
  responsibility — outside Shepherd scope.
- **Validates working tree is clean** — `git status --porcelain` must be empty. Fail hard
  with a clear error listing dirty files if not. This prevents pre-existing uncommitted
  human work from being silently mixed into the first agent commit.
  (ref.ap.QL051Wl21jmmYqTQTLglf.E — see [Working Tree Validation](git.md#working-tree-validation--startup-guard))
- **Records originating branch** — calls `getCurrentBranch()` before `createAndCheckout` to
  capture the branch the try branch is created from. Stored on `TicketShepherd` as
  `originatingBranch`, used by `TicketFailureLearningUseCase`
  (ref.ap.cI3odkAZACqDst82HtxKa.E) for harness-owned best-effort propagation of failure
  learning. `tryNumber` is also passed through to `TicketShepherd`.
- **Resolves try-N** — scans `.ai_out/` directories (single source of truth) to find the first
  N where no `.ai_out/` directory exists (see [Try-N Resolution](git.md#try-n-resolution)
  ref.ap.THL21SyZzJhzInG2m4zl2.E)
- **Creates feature branch** (`git checkout -b`) from current HEAD
- Initializes `SessionsState` (internal to `AgentFacadeImpl`)
- Constructs `AgentFacadeImpl` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — wires `SessionsState`,
  `AgentTypeAdapter` (ref.ap.A0L92SUzkG3gE0gX04ZnK.E), `TmuxSessionManager`,
  `TmuxCommunicator`, `ContextWindowStateReader`, and `UserQuestionHandler`
  (ref.ap.NE4puAzULta4xlOLh5kfD.E) into the facade. This is the single agent-facing
  dependency passed to all `PartExecutor` instances.
- Wires `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for instruction assembly
- Sets up `.ai_out/` directory structure for the branch (depends on branch name from previous steps)
- Creates the in-memory `CurrentState` (ref.ap.K3vNzHqR8wYm5pJdL2fXa.E) with `parts` array —
  for `with-planning` workflows, the planning part (`phase: "planning"`) from the workflow
  JSON's `planningParts`; for `straightforward` workflows, the execution parts
  (`phase: "execution"`) directly. Flushes to `current_state.json` on disk.
  (V2 adds resume-from-existing — ref.ap.LX1GCIjv6LgmM7AJFas20.E)
- Constructs `Clock` (ref.ap.whDS8M5aD2iggmIjDIgV9.E) — production `SystemClock`; tests
  inject `TestClock` for virtual time
- Constructs `TicketShepherd` with `ShepherdContext` + ticket-scoped state

## Not the Shepherd

The creator handles construction and ticket-scoped wiring. The shepherd handles orchestration
and lifecycle. `ShepherdContext` is shared infrastructure that outlives any single ticket —
the creator bridges it with ticket-specific state.
