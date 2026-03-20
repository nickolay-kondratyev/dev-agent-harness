# Clarification

## Requirements (Clear)

1. Create a **focused integration test** that verifies the PATH contains the callback scripts directory inside a tmux session
2. The test should NOT start a full Claude agent (no GLM/LLM needed)
3. This tests the actual tmux environment, not just the command string (existing unit test already covers command string)

## Scope

- New integration test class
- Uses existing `TmuxSessionManager` + `ClaudeCodeAdapter.buildStartCommand()` infrastructure
- Gated with `isIntegTestEnabled()` (requires tmux)
- Verifies PATH contains callback scripts dir inside tmux

## No Ambiguities

The ticket is specific enough to proceed to planning.
