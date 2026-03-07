# Clarification: Tmux + Claude Code

## Resolved Decisions
1. **Session naming**: `agent-harness__${uniqueId}` where uniqueId is timestamp-based
2. **Detached sessions**: tmux sessions run detached; keystrokes sent programmatically
3. **TmuxCommunicator**: New class wrapping tmux CLI via ProcessBuilder
4. **Existing code preserved**: InteractiveProcessRunner stays; new code is parallel path
5. **Complexity**: Moderate — THINK level planning

## No Blocking Ambiguities
All requirements are clear from the ticket description.
