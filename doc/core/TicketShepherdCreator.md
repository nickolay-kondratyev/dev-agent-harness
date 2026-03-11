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
- Parses the ticket (frontmatter `id`, `title`)
- Initializes `SessionsState`
- Wires `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for instruction assembly
- Sets up `.ai_out/` directory structure for the branch
- Creates or resumes `current_state.json`
- Constructs `TicketShepherd` with `ShepherdContext` + ticket-scoped state

## Not the Shepherd

The creator handles construction and ticket-scoped wiring. The shepherd handles orchestration
and lifecycle. `ShepherdContext` is shared infrastructure that outlives any single ticket —
the creator bridges it with ticket-specific state.
