# Implementation Private Context

## Status: COMPLETE

All phases implemented and tested. Integration test passes end-to-end.

## Key Discoveries During Implementation

### Claude CLI v2.1.63 Flags
- `--system-prompt-file` does NOT exist. Use `--system-prompt <text>` (inline text).
- `--append-system-prompt-file` does NOT exist. Use `--append-system-prompt <text>` (inline text).
- The factory reads the file and passes the content string to the starter.

### CLAUDECODE Environment Variable
- When running inside a Claude Code session, the `CLAUDECODE` env var is set.
- Claude Code refuses to start as a nested session when this var is present.
- Fix: `unset CLAUDECODE` in the bash -c command wrapper.
- This is essential for the harness's tmux spawn pattern.

### Agent Startup Delay
- Without a delay, sendKeys happens before Claude's interactive prompt is ready.
- The GUID gets consumed by bash (the shell wrapper), not by Claude.
- 5-second default delay is sufficient; configurable via constructor.

### Test Environment
- `HOME` env var is empty in the test environment (set by claude-code sandbox).
- `System.getProperty("user.home")` returns `/home/node` correctly.
- Claude projects dir is at `/home/node/.claude/projects/`.

## Follow-up Tickets Created
- `nid_d47u5pku4ldixx23tyggd29ep_E` - Implement ResumeTmuxAgentSessionUseCase

## Commits
1. `9524dca` - Initial implementation: data types, starter, chooser, factory, use case, unit tests
2. `276730a` - Fix Claude CLI integration: correct flags, unset CLAUDECODE, add startup delay
