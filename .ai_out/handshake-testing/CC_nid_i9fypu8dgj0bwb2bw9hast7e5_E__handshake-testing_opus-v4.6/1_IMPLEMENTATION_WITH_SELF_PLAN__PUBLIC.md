# Fix Handshake Startup -- Agent Not Receiving Initial Instructions

## Summary

Fixed three missing pieces that prevented agents from completing the handshake protocol after tmux session creation:

1. **`TICKET_SHEPHERD_SERVER_PORT` not exported** -- `ClaudeCodeAdapter.buildStartCommand()` now exports the server port into the tmux session environment.
2. **`callback_shepherd.signal.sh` not on PATH** -- The callback scripts directory is now added to PATH in the tmux session.
3. **Bootstrap message too vague** -- Changed from `"Waiting for instructions."` to an explicit instruction to call `callback_shepherd.signal.sh started` immediately.

## Changes

### `ClaudeCodeAdapter.kt`
- Added `serverPort: Int` and `callbackScriptsDir: String` to the primary constructor and `create()` factory.
- `buildStartCommand()` now exports `TICKET_SHEPHERD_SERVER_PORT=$serverPort` and appends `callbackScriptsDir` to PATH in the inner command.

### `ContextInitializer.kt` (ContextInitializerImpl)
- Added `readServerPort()` -- reads from `TICKET_SHEPHERD_SERVER_PORT` env var (via `envVarReader`), with `serverPortOverride` bypass for integration tests.
- Added `resolveCallbackScriptsDir()` -- extracts `callback_shepherd.signal.sh` from classpath resources to a temp directory, with `callbackScriptsDirOverride` bypass for integration tests.
- Both values are passed to `ClaudeCodeAdapter.create()`.
- `forIntegTest()` uses sentinel values for port/scripts since `ServerPortInjectingAdapter` overrides them at command-build time.

### `SubPartConfigBuilder.kt`
- Updated `BOOTSTRAP_MESSAGE` from `"Waiting for instructions."` to an explicit instruction to call `callback_shepherd.signal.sh started` using the Bash tool immediately before doing anything else.

### Test Updates
- `ClaudeCodeAdapterTest.kt` -- all `ClaudeCodeAdapter.create()` and constructor calls updated with `serverPort` and `callbackScriptsDir`. Added two new test assertions for the new exports.
- `ContextInitializerTest.kt` -- added `TICKET_SHEPHERD_SERVER_PORT` to the fake env var reader.
- `ShepherdInitializerTest.kt`, `TicketShepherdCreatorTest.kt` (both copies) -- added `serverPort` and `callbackScriptsDir` to `ClaudeCodeAdapter.create()` calls in test helper functions.

## Design Decisions

1. **`serverPortOverride`/`callbackScriptsDirOverride` in `ContextInitializerImpl`**: Integration tests dynamically pick ports AFTER context initialization and inject them via `ServerPortInjectingAdapter`. The overrides avoid requiring the env var during integ test setup while keeping the production path reading from env.

2. **Classpath resource extraction**: For production use, `callback_shepherd.signal.sh` is extracted from the classpath to a temp directory at runtime. This works regardless of working directory or distribution format.

3. **`ServerPortInjectingAdapter` kept as-is**: The integration test adapter wrapper still works. The native port/scripts in the adapter are overridden by the wrapper. Simplification is a follow-up.

## Test Results

All tests pass (`./gradlew :app:test` exit code 0). Key test files:
- `ClaudeCodeAdapterTest`: 42 tests, 0 failures (2 new assertions)
- `SubPartConfigBuilderTest`: 27 tests, 0 failures
- `ContextInitializerTest`: 5 tests, 0 failures
- `ShepherdInitializerTest`: 9 tests, 0 failures
