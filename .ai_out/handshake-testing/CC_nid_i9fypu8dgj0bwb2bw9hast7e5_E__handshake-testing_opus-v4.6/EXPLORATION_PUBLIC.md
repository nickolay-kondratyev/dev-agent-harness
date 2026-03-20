# Exploration: Handshake Startup Failure

## Root Cause: THREE Missing Pieces

### Issue 1: `TICKET_SHEPHERD_SERVER_PORT` not exported in tmux session
- `ClaudeCodeAdapter.buildStartCommand()` exports `TICKET_SHEPHERD_HANDSHAKE_GUID` but NOT `TICKET_SHEPHERD_SERVER_PORT`
- `callback_shepherd.signal.sh` requires both — fails immediately without port
- Integration tests work around this via `ServerPortInjectingAdapter` wrapper

### Issue 2: `callback_shepherd.signal.sh` not on PATH in tmux session
- Script lives at `app/src/main/resources/scripts/`
- Integration tests extract path and inject via `ServerPortInjectingAdapter`
- Production code never injects it

### Issue 3: Bootstrap message doesn't instruct handshake
- Production: `"Waiting for instructions."` — doesn't tell agent to call `started`
- Integration tests: `IntegTestCallbackProtocol.BOOTSTRAP_MESSAGE` — explicitly tells agent to call `started` immediately

## Key Files

| File | Role |
|------|------|
| `app/src/main/kotlin/.../agent/adapter/ClaudeCodeAdapter.kt` | Builds tmux start command — missing port + scripts PATH exports |
| `app/src/main/kotlin/.../executor/SubPartConfigBuilder.kt` | Defines bootstrap message — too vague |
| `app/src/main/kotlin/.../initializer/ContextInitializerImpl.kt` | Wires `ClaudeCodeAdapter` — doesn't pass port/scripts |
| `app/src/test/.../integtest/ServerPortInjectingAdapter.kt` | Test workaround that shows what's missing in production |
| `app/src/main/resources/scripts/callback_shepherd.signal.sh` | The callback script that agents need on PATH |

## Fix Strategy

1. **`ClaudeCodeAdapter`**: Add `serverPort: Int` and `callbackScriptsDir: String` constructor params. Export in start command.
2. **`ContextInitializerImpl`**: Read server port from env, resolve scripts dir, pass to adapter.
3. **`SubPartConfigBuilder`**: Update `BOOTSTRAP_MESSAGE` to instruct `callback_shepherd.signal.sh started`.
4. **Tests**: Update `ClaudeCodeAdapterTest` for new exports. Consider simplifying/removing `ServerPortInjectingAdapter`.
