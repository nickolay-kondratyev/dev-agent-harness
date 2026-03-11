# TicketShepherd / ap.P3po8Obvcjw4IXsSUSU91.E

The central coordinator that drives a ticket through its entire workflow lifecycle.
`TicketShepherd` is the one moving through the motions — it walks the workflow, spawns agents,
reacts to their signals, and decides what happens next.

## How It Drives the Workflow

1. Initial setup (branch creation, `current_state.json` initialization)
2. Iterates through the parts in order
3. For each part, iterates through sub-parts:
   - Calls `SpawnTmuxAgentSessionUseCase` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) to spawn the agent
   - Sends instructions via TMUX `send-keys`
   - Waits for agent callback (`/agent/done`, `/agent/failed`, `/agent/question`)
4. On `/agent/done` from a reviewer: evaluates the iteration decision via `DirectLLMApi`
   - **Pass** → move to next part
   - **Fail** → resume doer's TMUX session with new instructions, then resume reviewer
5. On `/agent/failed` → delegates to `FailedToExecutePlanUseCase`
6. On `/agent/question` → presents to human, sends answer back via TMUX `send-keys`
7. When a part completes, kills all TMUX sessions for that part
8. Handles git commits between sub-parts

## Responsibilities

- **Owns `SessionsState`** — tracks live `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E)
  instances, keyed by HandshakeGuid.
- **Receives server callbacks** — the `ShepherdServer` routes agent callbacks (`/agent/done`,
  `/agent/question`, `/agent/failed`, `/agent/status`) to `TicketShepherd` after looking up
  the HandshakeGuid in `SessionsState`.
- **Orchestrates use cases** — calls use cases for discrete operations; the shepherd makes
  the decisions, use cases do the work.
- **Drives iteration decisions** — on `/agent/done`, determines whether to loop back (resume
  doer session) or proceed to the next sub-part/part, based on LLM evaluation via `DirectLLMApi`.
- **Manages part lifecycle** — spawns TMUX sessions for sub-parts within a part, keeps them
  alive across iterations, and kills them when the part completes.
- **Monitors agent health** — triggers `NoStatusCallbackTimeOutUseCase` and
  `NoReplyToPingUseCase` when agents go silent.

## Dependencies

Receives `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E) for shared infrastructure
(tmux, LLM, use cases) plus ticket-scoped state (`SessionsState`, parsed workflow, ticket
metadata) wired by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E).

## Not a Use Case

`TicketShepherd` is a long-lived coordinator, not a discrete operation. It lives for the
duration of a ticket's processing and holds mutable state (`SessionsState`). Use cases are
stateless operations it delegates to.
