# Exploration: AgentFacadeImpl Integration Test

## Key Architecture

### AgentFacadeImpl (4 methods)
- **File**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`
- **Interface**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt`
- Methods: `spawnAgent`, `sendPayloadAndAwaitSignal`, `readContextWindowState`, `killSession`

### Constructor Dependencies (11 params, all DIP)
```
SessionsState, AgentTypeAdapter, TmuxSessionCreator, SingleSessionKiller,
ContextWindowStateReader, Clock, HarnessTimeoutConfig, AckedPayloadSender,
AgentUnresponsiveUseCase, QaDrainer, OutFactory
```

### Signal Flow (critical for test)
1. `spawnAgent` → creates tmux session → waits for `/signal/started` HTTP callback → resolves session ID
2. `sendPayloadAndAwaitSignal` → sends via tmux send-keys → awaits signal completion via HTTP callback
3. HTTP callbacks handled by `ShepherdServer` (Ktor CIO) → completes `SessionEntry.signalDeferred`

### HTTP Server
- **ShepherdServer** at `app/src/main/kotlin/com/glassthought/shepherd/core/server/ShepherdServer.kt`
- Routes: `POST /callback-shepherd/signal/{started|done|user-question|fail-workflow|ack-payload|self-compacted}`
- Shares `SessionsState` with `AgentFacadeImpl`

### Callback Script
- `app/src/main/resources/scripts/callback_shepherd.signal.sh`
- Requires env vars: `TICKET_SHEPHERD_SERVER_PORT`, `TICKET_SHEPHERD_HANDSHAKE_GUID`
- `HANDSHAKE_GUID` is exported by `ClaudeCodeAdapter.buildStartCommand()`
- `SERVER_PORT` must be in environment (inherited by tmux session)

### Test Infrastructure
- **SharedContextDescribeSpec**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt`
- Provides `shepherdContext` with tmux.sessionManager, claudeCode.agentTypeAdapter
- **isIntegTestEnabled()**: `app/src/test/kotlin/com/glassthought/bucket/integTestSupport.kt`
- **GLM**: `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md`

### What ShepherdContext provides vs what test needs to wire
**Available from ShepherdContext**:
- `infra.tmux.sessionManager` (implements TmuxSessionCreator)
- `infra.claudeCode.agentTypeAdapter` (ClaudeCodeAdapter with GLM)
- `infra.outFactory`
- `timeoutConfig` (HarnessTimeoutConfig)

**Must be wired in test**:
- `SessionsState` (new instance, shared with ShepherdServer)
- `ShepherdServer` (start on random port)
- `SystemClock` / Clock
- `AckedPayloadSenderImpl`
- `AgentUnresponsiveUseCaseImpl`
- `QaDrainer` (can use no-op for basic test)
- `ContextWindowStateReader` (ClaudeCodeContextWindowStateReader or test stub)
- `SingleSessionKiller` (delegate to sessionManager.killSession)

### Key Challenge: Full E2E chain
The agent (GLM) must:
1. Be instructed (via system prompt) to call `callback_shepherd.signal.sh started`
2. Know the server port (via `TICKET_SHEPHERD_SERVER_PORT` env var)
3. Have `callback_shepherd.signal.sh` on PATH
4. Process ACK-wrapped payloads and call `callback_shepherd.signal.sh ack-payload`
5. Call `callback_shepherd.signal.sh done completed` when done

### Existing Pattern
- `ClaudeCodeAdapterSpawnIntegTest` tests at adapter level (no server needed)
- The new test goes higher — tests full `AgentFacadeImpl` chain

### Key Types
| Type | Location |
|------|----------|
| SpawnAgentConfig | `core/agent/facade/SpawnAgentConfig.kt` |
| SpawnedAgentHandle | `core/agent/facade/SpawnedAgentHandle.kt` |
| AgentSignal | `core/agent/facade/AgentSignal.kt` |
| ContextWindowState | `core/agent/facade/ContextWindowState.kt` |
| SessionEntry | `core/session/SessionEntry.kt` |
| HandshakeGuid | `core/agent/sessionresolver/HandshakeGuid.kt` |
| HarnessTimeoutConfig | `core/data/HarnessTimeoutConfig.kt` |
| Clock/SystemClock | `core/time/Clock.kt` |
