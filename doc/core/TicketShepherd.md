# TicketShepherd / ap.P3po8Obvcjw4IXsSUSU91.E

The central coordinator that drives a ticket through its entire workflow lifecycle.
`TicketShepherd` is the one moving ticket through the motions — it walks the workflow, spawns agents,
reacts to their signals, and decides what happens next.

## How It Drives the Workflow

1. Initial setup (branch creation, `current_state.json` initialization)
2. Iterates through the parts in order
3. For each part, iterates through sub-parts:
   - Calls `SpawnTmuxAgentSessionUseCase` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) to spawn the agent
   - Sends instructions via TMUX `send-keys`
   - Waits for agent callback (`/callback-shepherd/done`, `/callback-shepherd/fail-workflow`, `/callback-shepherd/user-question`)
4. On `/callback-shepherd/done`: reads `result` field, validates against sub-part role:
   - `result: "completed"` (doer) → move to reviewer (or complete part if single sub-part)
   - `result: "pass"` (reviewer) → move to next part
   - `result: "needs_iteration"` (reviewer) → check iteration counter:
     - Within budget → send new instructions to doer's TMUX session via `send-keys`, then send to reviewer
     - Exceeds `iteration.max` → `FailedToConvergeUseCase`
5. On `/callback-shepherd/fail-workflow` → delegates to `FailedToExecutePlanUseCase`
6. On `/callback-shepherd/user-question` → presents to human, sends answer back via TMUX `send-keys`
7. When a part completes, kills all TMUX sessions for that part
8. Handles git commits between sub-parts

## Responsibilities

- **Owns `SessionsState`** (ref.ap.7V6upjt21tOoCFXA7nqNh.E) — tracks live `TmuxAgentSession`
  (ref.ap.DAwDPidjM0HMClPDSldXt.E) instances, keyed by HandshakeGuid.
- **Receives server callbacks** — the `ShepherdServer` routes agent callbacks (`/callback-shepherd/done`,
  `/callback-shepherd/user-question`, `/callback-shepherd/fail-workflow`, `/callback-shepherd/ping-ack`)
  to `TicketShepherd` after looking up the HandshakeGuid in `SessionsState`.
- **Orchestrates use cases** — calls use cases for discrete operations; the shepherd makes
  the decisions, use cases do the work.
- **Drives iteration decisions** — on `/callback-shepherd/done`, reads the reviewer's `result`
  field to determine whether to loop back (send new instructions to doer) or proceed to the next
  sub-part/part. The reviewer's verdict is authoritative — no LLM evaluation in this path.
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
