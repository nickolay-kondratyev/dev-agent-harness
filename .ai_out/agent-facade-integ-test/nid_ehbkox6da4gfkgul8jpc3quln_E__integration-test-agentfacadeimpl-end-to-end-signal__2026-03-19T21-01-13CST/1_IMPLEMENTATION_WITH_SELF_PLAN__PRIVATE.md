# Private Context: AgentFacadeImpl Integration Test

## Status: COMPLETE

## Implementation Details

### File Created
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`

### Key Architecture Decisions

1. **ServerPortInjectingAdapter**: Decorator wrapping `ClaudeCodeAdapter` that injects `TICKET_SHEPHERD_SERVER_PORT` and PATH into the bash -c inner command. This avoids modifying production code.

2. **Random port via ServerSocket(0)**: Picks a random port, then starts Ktor CIO on that port. Used `ServerSocket(0).use { it.localPort }` instead of Ktor's `resolvedConnectors()` because of Ktor 3.x API resolution issues at compile time.

3. **System prompt file**: Created as temp file in `.tmp/` directory. Instructs the agent to call `callback_shepherd.signal.sh started` first, ACK payloads, and signal done.

4. **Stub ContextWindowStateReader**: Returns `ContextWindowState(remainingPercentage = null)` since we're testing signal flow, not context window monitoring.

5. **No-op QaDrainer**: `QaDrainer { _, _ -> }` since Q&A is not tested in this E2E test.

6. **Integration timeout config**: 3min startup, 5min normal activity, 1min ping response, 5s health check interval. Generous enough for GLM but faster than production defaults.

### Dependencies Wired

From SharedContextDescribeSpec:
- `shepherdContext.infra.tmux.sessionManager` → TmuxSessionCreator + SingleSessionKiller
- `shepherdContext.infra.claudeCode.agentTypeAdapter` → ClaudeCodeAdapter with GLM
- `shepherdContext.infra.outFactory`

Wired in test:
- `SessionsState()` — shared with ShepherdServer
- `ShepherdServer` on random port
- `SystemClock()`
- `AckedPayloadSenderImpl` with 2min ack timeout
- `AgentUnresponsiveUseCaseImpl`
- `ServerPortInjectingAdapter` wrapping real adapter

### Build Verification
- `./gradlew :app:test` passes (unit tests + detekt)
- `./gradlew :app:compileTestKotlin` compiles cleanly
- Integration tests gated by `isIntegTestEnabled()` — not run in CI by default
