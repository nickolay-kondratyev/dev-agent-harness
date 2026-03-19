# Exploration: AgentFacadeImpl Implementation

## Task
Implement `AgentFacadeImpl` — the real `AgentFacade` implementation that wires to infra components.

## Key Files

### Interface & Types (all exist, read-only)
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt` — 4-method interface
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnedAgentHandle.kt` — guid, sessionId, lastActivityTimestamp
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnAgentConfig.kt` — partName, subPartName, subPartIndex, agentType, model, role, systemPromptPath, bootstrapMessage
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt` — sealed: Done, FailWorkflow, Crashed, SelfCompacted
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentPayload.kt` — instructionFilePath
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/ContextWindowState.kt` — remainingPercentage: Int?

### Infrastructure Dependencies (all exist)
- `SessionsState` — `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionsState.kt` — register(guid, entry), lookup(guid), removeAllForPart(partName)
- `SessionEntry` — `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` — tmuxAgentSession, partName, subPartName, subPartIndex, signalDeferred, lastActivityTimestamp, pendingPayloadAck, questionQueue
- `AgentTypeAdapter` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/AgentTypeAdapter.kt` — buildStartCommand(params), resolveSessionId(guid)
- `BuildStartCommandParams` — same file — bootstrapMessage, handshakeGuid, workingDir, model, tools, systemPromptFilePath, appendSystemPrompt
- `TmuxSessionManager` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt` — createSession(name, cmd), killSession(session)
- `TmuxCommunicator` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxCommunicator.kt` — sendKeys(pane, text), sendRawKeys(pane, keys)
- `ContextWindowStateReader` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/contextwindow/ContextWindowStateReader.kt` — read(agentSessionId): ContextWindowState
- `UserQuestionHandler` — `app/src/main/kotlin/com/glassthought/shepherd/core/question/UserQuestionHandler.kt` — handleQuestion(ctx): String
- `AgentUnresponsiveUseCase` — `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt` — handle(detCtx, tmuxSession, diag)
- `HarnessTimeoutConfig` — `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` — healthTimeouts, healthCheckInterval, payloadAckTimeout, etc.
- `Clock` — `app/src/main/kotlin/com/glassthought/shepherd/core/time/Clock.kt` — fun interface, now(): Instant
- `AckedPayloadSender` — `app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt` — sendAndAwaitAck(tmuxSession, entry, content)
- `HandshakeGuid` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt` — generate(), value class
- `ResumableAgentSessionId` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt`
- `TmuxAgentSession` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/TmuxAgentSession.kt` — tmuxSession + resumableAgentSessionId
- `TmuxSession` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSession.kt` — name, paneTarget, sendKeys, sendRawKeys, exists

### Reference Implementation
- `SpawnTmuxAgentSessionUseCase` — `app/src/main/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnTmuxAgentSessionUseCase.kt` — shows spawn flow (generate GUID, build command, create session, await startup, resolve session ID). AgentFacadeImpl.spawnAgent will follow similar flow.

### Test Infrastructure (all exist)
- `FakeAgentFacade` — `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt` — already exists, programmable fake
- `TestClock` — `app/src/test/kotlin/com/glassthought/shepherd/core/time/TestClock.kt` — advance(duration), set(instant)
- `ShepherdValType` — `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` — structured logging types

## Key Design Points

1. **SessionsState is INTERNAL** to AgentFacadeImpl — PartExecutor never accesses it.
2. **signalDeferred lifecycle** is fully owned by this class.
3. **spawnAgent** flow: generate HandshakeGuid → build start command via AgentTypeAdapter → create TMUX session → await startup signal → resolve session ID → register SessionEntry → return SpawnedAgentHandle.
4. **killSession**: delegates to TmuxSessionManager.killSession, no SessionsState removal needed (SessionEntry is per-invocation of sendPayloadAndAwaitSignal).
5. **readContextWindowState**: delegates to ContextWindowStateReader.read(sessionId).
6. **sendPayloadAndAwaitSignal**: stub initially — full health-aware await loop is separate ticket (nid_qdd1w86a415xllfpvcsf8djab_E). Minimal: create fresh CompletableDeferred, re-register SessionEntry, send payload via AckedPayloadSender, await signal.

## SpawnAgentConfig vs SpawnTmuxAgentSessionParams
- SpawnAgentConfig has: partName, subPartName, subPartIndex, agentType, model, role, systemPromptPath, bootstrapMessage
- SpawnTmuxAgentSessionParams has: partName, subPartName, agentType, model, workingDir, tools, systemPromptFilePath, appendSystemPrompt, bootstrapMessage, startedDeferred
- AgentFacadeImpl needs to bridge these: it creates the startedDeferred internally (as part of the initial SessionEntry registration) and maps systemPromptPath to systemPromptFilePath string.
- Note: SpawnAgentConfig doesn't have workingDir, tools, appendSystemPrompt — these may need to come from constructor config or be added. IMPORTANT: workingDir can default to pwd, tools could be a config value.

## Testing Pattern
- Unit tests extend `AsgardDescribeSpec`
- BDD: describe("GIVEN...") / describe("WHEN...") / it("THEN...")
- `outFactory` inherited from AsgardDescribeSpec
- One assert per `it` block
- Fakes fail hard by default (throw if not programmed)
