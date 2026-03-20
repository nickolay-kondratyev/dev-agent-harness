# Implementation Private State

## Status: COMPLETE (Iteration 2 - review feedback addressed)

## What was done
1. Created `StraightforwardWorkflowE2EIntegTest.kt` in `integtest/e2e/` package
2. Modified `test_with_integ.sh` to run `installDist` before tests
3. All unit tests pass

## Iteration 2 changes
- Added comment explaining `--iteration-max 1` is a no-op (DEFERRED in ShepherdInitializer)
- Changed temp dir from system `/tmp/` to `projectRoot/.tmp/`
- Moved `listTmuxSessions()` from free-floating function into companion object
- `runGitInTemp` now checks exit codes with `require(exitCode == 0)` and captures stderr

## Open items for follow-up
- The test relies on GLM env var inheritance through tmux. If `ClaudeCodeAdapter.buildStartCommand()` ever adds `unset ANTHROPIC_BASE_URL`, this test will silently start using real Anthropic API. Consider a follow-up ticket for a `--glm` CLI flag.
- The test can only run inside Docker (EnvironmentValidator checks `/.dockerenv`).
