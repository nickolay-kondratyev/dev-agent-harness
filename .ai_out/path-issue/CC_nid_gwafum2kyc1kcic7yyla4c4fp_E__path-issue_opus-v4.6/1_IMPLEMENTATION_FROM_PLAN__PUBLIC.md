# Implementation: Integration Test for PATH Verification in tmux Sessions

## What Was Done

Created a focused integration test that verifies the `PATH` environment variable is correctly
set inside a tmux session when using the command built by `ClaudeCodeAdapter.buildStartCommand()`.

### New File

- `app/src/test/kotlin/com/glassthought/shepherd/integtest/TmuxPathIntegTest.kt`

### Approach

1. Built the real command via `ClaudeCodeAdapter.buildStartCommand()` with dummy params
2. Replaced `claude --model ...` through the closing single quote with `echo $PATH > <tmpFile>`
3. Started a tmux session with the modified command via `TmuxSessionManager.createSession()`
4. Polled for the output file using `AsgardAwaitility`
5. Asserted the captured PATH contains `callbackScriptsDir.path`

### Review Feedback Applied

1. **Simplified to one `it` block** -- Single assertion: `capturedPath shouldContain callbackScriptsDir.path`
2. **Used internal constructor** -- `ClaudeCodeAdapter(guidScanner = GuidScanner { emptyList() }, ...)` instead of `.create()`
3. **Simple command replacement** -- Find `claude --model` in the command, replace from there to the closing `'` with `echo $PATH > tmpFile`

### Design Decisions

- **Base class**: Used `SharedContextDescribeSpec` for consistency with existing tmux integration tests
  (`TmuxSessionManagerIntegTest`, `TmuxCommunicatorIntegTest`), even though the test only needs tmux.
- **Cleanup pattern**: Followed `TmuxCommunicatorIntegTest` exactly with `createdSessions` and `createdFiles`
  lists cleaned up in `afterEach`.
- **Temp file location**: `.tmp/path-integ-test-output-<timestamp>` per project convention.
- **Session name**: `path-integ-<timestamp>` prefix, consistent with other tests (`test-comm-`, `test-exists-`).

### Test Verification

- `./gradlew :app:test` (unit tests only) -- BUILD SUCCESSFUL
- `./gradlew :app:test -PrunIntegTests=true --tests "com.glassthought.shepherd.integtest.TmuxPathIntegTest"` -- BUILD SUCCESSFUL
- All tmux integration tests (`TmuxSessionManagerIntegTest`, `TmuxCommunicatorIntegTest`, `TmuxPathIntegTest`) -- BUILD SUCCESSFUL

### Acceptance Criteria Status

| AC | Status | Description |
|----|--------|-------------|
| AC-1 | DONE | `TmuxPathIntegTest` exists at expected path |
| AC-2 | DONE | Gated with `isIntegTestEnabled()`, requires only tmux |
| AC-3 | DONE | Uses `ClaudeCodeAdapter.buildStartCommand()` to generate command |
| AC-4 | DONE | Starts real tmux session via `TmuxSessionManager.createSession()` |
| AC-5 | DONE | Verifies PATH contains callback scripts directory |
| AC-6 | DONE | tmux sessions cleaned up in `afterEach` |
| AC-7 | DONE | Temp files cleaned up in `afterEach` |
| AC-8 | DONE | Test passes with `-PrunIntegTests=true` |
| AC-9 | DONE | BDD style with one assert per `it` block |
| AC-10 | DONE | All existing tests continue to pass |
