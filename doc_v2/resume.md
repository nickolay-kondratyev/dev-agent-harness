# Harness-Level Resume — V2 / ap.LX1GCIjv6LgmM7AJFas20.E

> **V2 feature** — not implemented in V1. V1 writes `current_state.json` for progress tracking
> but does not consume it on restart. If the harness dies, you start over.

## Motivation

Primary use case: electricity goes out mid-session — we don't want to lose all progress and restart
from scratch.

## Layered Resume Design

Resume has two layers, tried in order. Layer 1 is preferred; layer 2 is the fallback.

### Layer 1 — Resume Existing Agent Session

**Precondition:** The last `sessionIds` entry for the in-progress sub-part has a valid
`agent_session_id` (or `agent_session_path`), and the agent runtime supports `--resume`.

**Flow:**

1. Harness reads `current_state.json`, identifies the in-progress sub-part
2. Reads the last `sessionIds` entry — reuses the **same HandshakeGuid**
3. Spawns TMUX session with:
   `export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && claude --resume <agent_session_id>`
4. **Handshake verification**: Harness sends a health ping via TMUX `send-keys`, asking the agent
   to call `callback_shepherd.ping-ack.sh` with a `resumed` status confirming it came back up
   successfully
5. If ping-ack with `resumed` status received within timeout → **Layer 1 success**. Agent continues
   where it left off with full conversation history intact.

**Why handshake:** A `--resume` invocation can silently fail (session corrupted, model mismatch,
context too large). The ping-ack handshake confirms the agent is actually alive and functional
after resume — not just that the process started.

### Layer 2 — New Agent Session for In-Progress Sub-Part

**Trigger:** Layer 1 failed — either no resumable session ID exists, `--resume` failed to start,
or the ping-ack handshake timed out.

**Flow:**

1. Kill the failed TMUX session (if any)
2. Start a **new** agent session for the same sub-part (full spawn flow —
   ref.ap.hZdTRho3gQwgIXxoUtTqy.E)
3. Append the new session record to the sub-part's `sessionIds` array
4. Agent starts fresh but with the same instruction context (role + ticket + SHARED_CONTEXT.md +
   prior PUBLIC.md files)

**Trade-off:** The agent loses its conversation history from the previous session. But it has
all the file-based context, so it can pick up the task. This is strictly better than restarting
the entire workflow.

## Harness Restart Flow (V2)

On `shepherd run`, if `current_state.json` exists for the given ticket+branch:

1. **Interactive prompt** asks: resume from checkpoint or start fresh?
2. **Resume from checkpoint**:
   - Skip completed sub-parts
   - For the in-progress sub-part, attempt Layer 1 → Layer 2 resume
3. **Start fresh**:
   - Wipe `current_state.json`
   - Begin from part 1 (same branch, no try-N increment — this is not a failure retry)

### Git State on Resume

- Committed changes from completed sub-parts are preserved (the harness commits between sub-parts)
- Uncommitted changes from the in-progress sub-part are kept as-is — the resumed agent inherits
  them. The agent's instruction file will note that prior work may exist.

## Within-Run Crash Recovery (V2)

The same layered resume applies to `NoReplyToPingUseCase` (agent crashes while harness is alive):

1. Harness detects agent is unresponsive (no ping-ack after timeout)
2. Kill the TMUX session
3. Attempt Layer 1 → Layer 2 resume for the crashed sub-part

**V1 behavior:** On agent crash, harness starts a new agent session (Layer 2 only, no Layer 1
attempt). This avoids the complexity of the handshake verification protocol.

## Open Questions

- Should Layer 1 resume reuse the same TMUX session name or create a new one?
- Should the `resumed` status in ping-ack include agent-side diagnostics (e.g., context window
  usage, conversation turn count)?
- How many Layer 1 attempts before falling through to Layer 2? (Proposal: exactly 1)

## Risks — Try-N Interaction

V1 creates a new try on every `shepherd run` (ref.ap.THL21SyZzJhzInG2m4zl2.E). V2 resume
must **not** increment try-N — it must re-checkout the existing branch and pick up where it
left off. New try-N only on `FailedToExecutePlanUseCase` (which is a fresh start, not a resume).

`TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) will need a resume-vs-fresh-start
code path: on resume, derive the branch name from existing state rather than running the
try-N resolution algorithm.
