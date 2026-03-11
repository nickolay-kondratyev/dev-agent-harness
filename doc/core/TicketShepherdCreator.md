# TicketShepherdCreator / ap.cJbeC4udcM3J8UFoWXfGh.E

Creates a fully wired `TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E) for a single run.

## Responsibility

Receives `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E) plus ticket-specific inputs
(ticket path, workflow name), resolves ticket-scoped dependencies, and returns a ready-to-go
`TicketShepherd`. One shepherd per run — the creator is called once from the CLI entry point.

## Inputs

- `ShepherdContext` — shared infrastructure (tmux, LLM, logging, use cases). Already
  initialized by `Initializer` before the creator runs.
- Ticket path — the markdown file to process
- Workflow name — which workflow JSON to load

## What It Does

- Resolves workflow JSON from `config/workflows/<name>.json`
- Parses the ticket (frontmatter `id`, `title`, `status`)
- **Validates ticket status** — `status` must be `in_progress`. Fail hard with clear error
  if not. Marking the ticket as `in_progress` and pushing to remote is the caller's
  responsibility — outside Shepherd scope.
- **Resolves try-N** — scans both local branches and `.ai_out/` directories to find the first
  N where neither exists (see [Try-N Resolution](../high-level.md#try-n-resolution)
  ref.ap.THL21SyZzJhzInG2m4zl2.E)
- **Creates feature branch** (`git checkout -b`) from current HEAD
- Initializes `SessionsState`
- Wires `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for instruction assembly
- Sets up `.ai_out/` directory structure for the branch (depends on branch name from previous steps)
- Creates `current_state.json` (V2 adds resume-from-existing — ref.ap.LX1GCIjv6LgmM7AJFas20.E)
- Constructs `TicketShepherd` with `ShepherdContext` + ticket-scoped state

## Not the Shepherd

The creator handles construction and ticket-scoped wiring. The shepherd handles orchestration
and lifecycle. `ShepherdContext` is shared infrastructure that outlives any single ticket —
the creator bridges it with ticket-specific state.
