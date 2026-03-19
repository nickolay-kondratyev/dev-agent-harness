# Implementation: AgentFacadeImpl End-to-End Integration Test

## What Was Done

Created `AgentFacadeImplIntegTest.kt` — an end-to-end integration test for `AgentFacadeImpl` that validates the full signal flow from agent spawn through HTTP callback to signal completion.

## Architecture

### Test Infrastructure Wiring

The test wires a complete `AgentFacadeImpl` with:
- **Real infrastructure from `SharedContextDescribeSpec`**: tmux session manager, ClaudeCodeAdapter (with GLM), outFactory
- **Test-scoped Ktor CIO HTTP server**: `ShepherdServer` started on a random port, sharing `SessionsState` with the facade
- **`ServerPortInjectingAdapter`**: Decorator pattern wrapping `ClaudeCodeAdapter` to inject `TICKET_SHEPHERD_SERVER_PORT` and callback script PATH into the tmux session's environment

### Key Design Decision: ServerPortInjectingAdapter

The `ClaudeCodeAdapter.buildStartCommand()` produces a `bash -c '...'` command. The adapter injects environment exports at the start of the inner command:
```
bash -c 'export TICKET_SHEPHERD_SERVER_PORT=<port> && export PATH=$PATH:<scripts_dir> && <original inner command>'
```

This avoids modifying production code while enabling the callback script to reach the test server. The adapter includes a coupling comment noting its dependency on ClaudeCodeAdapter's command format.

## Test Scenarios

| Scenario | What's Tested |
|----------|---------------|
| **spawnAgent — HandshakeGuid + session ID** | Full spawn chain: tmux session creation, GLM agent startup, `/signal/started` HTTP callback, session ID resolution. Both HandshakeGuid and sessionId validated in single spawn. |
| **sendPayloadAndAwaitSignal — done(COMPLETED)** | ACK-wrapped payload delivery, agent processes, `/signal/done` HTTP callback, `AgentSignal.Done(COMPLETED)` returned. Instruction file cleanup via `try/finally`. |
| **killSession** | Session killed, removed from `SessionsState` |
| **readContextWindowState** | Returns `ContextWindowState` with `remainingPercentage == null` from stub reader |

## Review Feedback Addressed

1. **Merged two spawnAgent `it` blocks** into one — avoids spawning 2 GLM agents for related assertions
2. **Extracted `buildSpawnConfig(partName)`** helper — DRYs up 4 copies of SpawnAgentConfig construction
3. **Fixed instruction file leak** — wrapped in `try/finally` so cleanup happens even on assertion failure
4. **Fixed tautological assertion** — changed `state shouldNotBe null` to `state.remainingPercentage shouldBe null`
5. **Moved free-floating functions into `IntegTestHelpers` object** — aligns with CLAUDE.md standards
6. **Used `shouldBeInstanceOf<AgentSignal.Done>().result`** chain — replaces two-step cast pattern
7. **Added coupling comment on `ServerPortInjectingAdapter`** — documents dependency on ClaudeCodeAdapter's `bash -c '...'` format

## Files

- `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`

## Running

```bash
./gradlew :app:test -PrunIntegTests=true
```
