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

This avoids modifying production code while enabling the callback script to reach the test server.

## Test Scenarios

| Scenario | What's Tested |
|----------|---------------|
| **spawnAgent — valid HandshakeGuid** | Full spawn chain: tmux session creation → GLM agent startup → `/signal/started` HTTP callback → session ID resolution |
| **spawnAgent — resolved session ID** | Session ID resolution via JSONL file scanning after startup |
| **sendPayloadAndAwaitSignal — done(COMPLETED)** | ACK-wrapped payload delivery → agent processes → `/signal/done` HTTP callback → `AgentSignal.Done(COMPLETED)` returned |
| **killSession** | Session killed → removed from `SessionsState` |
| **readContextWindowState** | Returns `ContextWindowState` (stub reader, null remainingPercentage) |

## Files Created

- `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`

## Running

```bash
./gradlew :app:test -PrunIntegTests=true
```
