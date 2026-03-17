# Idle Session Recovery — V2

## Problem

In `PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E), both TMUX sessions stay
alive for the entire part lifecycle. While one agent is actively working, the other is idle.
If the idle session dies (OOM, external `tmux kill-session`, terminal crash), the executor
discovers it only when it tries to `send-keys` for the next turn.

V1 treats this as `PartResult.AgentCrashed` — logs ERROR, halts, waits for human. This is
acceptable because idle session death is rare in practice.

## V2 Design Direction

Automatic respawn of the dead idle session:

1. Detect `send-keys` failure (after retry)
2. Spawn a **new** TMUX session for the dead sub-part
3. Use `--resume` with the `agentSession.id` from `current_state.json` to restore the
   agent's conversation context
4. Send the new instructions to the respawned session
5. Continue the iteration loop as normal

### Considerations

- The respawned session gets a **new** HandshakeGuid (new lifecycle) but resumes the
  **same** agent conversation via `--resume`
- `current_state.json` gets a new `sessionIds` entry appended (session history preserved)
- The agent's context window may have been compacted — the resumed session may not have
  full history. The instruction file should contain sufficient context regardless.
- If respawn also fails → fall back to V1 behavior (crash, halt)
