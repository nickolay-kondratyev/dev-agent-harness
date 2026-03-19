# Exploration: SpawnTmuxAgentSessionUseCase

## Key Files for Implementation

| Component | Path | Notes |
|-----------|------|-------|
| Spec | `doc/use-case/SpawnTmuxAgentSessionUseCase.md` | ref.ap.hZdTRho3gQwgIXxoUtTqy.E |
| HandshakeGuid | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt` | `@JvmInline value class`, `generate()` factory |
| AgentTypeAdapter | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/AgentTypeAdapter.kt` | `buildStartCommand(BuildStartCommandParams): TmuxStartCommand`, `resolveSessionId(HandshakeGuid): String` |
| TmuxSessionManager | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt` | `createSession(sessionName: String, startCommand: TmuxStartCommand): TmuxSession`, throws `IllegalStateException` on failure |
| TmuxAgentSession | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/TmuxAgentSession.kt` | `data class(tmuxSession: TmuxSession, resumableAgentSessionId: ResumableAgentSessionId)` |
| ResumableAgentSessionId | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt` | `data class(handshakeGuid, agentType, sessionId, model)` |
| CurrentState | `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt` | `addSessionRecord(partName, subPartName, record)` |
| SessionRecord | `app/src/main/kotlin/com/glassthought/shepherd/core/state/SessionRecord.kt` | `data class(handshakeGuid: String, agentSession: ???, agentType: String, model: String, timestamp: String)` — NOTE: `AgentSessionInfo` class doesn't exist yet |
| SpawnAgentConfig | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnAgentConfig.kt` | Has all fields needed: partName, subPartName, agentType, model, etc. |
| SpawnedAgentHandle | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnedAgentHandle.kt` | Regular class (not data class), identity by guid |
| AgentSignal | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt` | `sealed class: Done, FailWorkflow, Crashed, SelfCompacted` |
| HealthTimeoutLadder | `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` | `startup: Duration = 3.minutes`, `forTests()` variant with 1s |
| AgentUnresponsiveUseCase | `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt` | fun interface with DetectionContext enum |
| TmuxSessionName | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/data/TmuxSessionName.kt` | `data class(sessionName: String)` |

## Design Decisions from Spec

1. **Return type**: `TmuxAgentSession` (NOT `SpawnedAgentHandle` — caller wraps)
2. **Startup await**: Receives `CompletableDeferred<Unit>` completed by server on `/started` callback
3. **On TMUX failure**: Throw exception — caller catches and maps to `AgentSignal.Crashed`
4. **TmuxSessionName format**: `shepherd_${partName}_${subPartName}`
5. **Session record**: Created and stored after successful startup await + session ID resolution
6. **AgentTypeAdapter dispatch**: Use case selects adapter by `agentType` from config

## `SessionRecord` Gap

The `SessionRecord` data class currently has fields: `handshakeGuid: String, agentSession: AgentSessionInfo, agentType: String, model: String, timestamp: String` per the schema spec, BUT `AgentSessionInfo` class doesn't exist in code. The actual `SessionRecord` in code only has these string fields. The implementation needs to work with the existing code or create `AgentSessionInfo` if needed.

Actually, upon re-reading the code, `SessionRecord` has: `handshakeGuid: String, agentSession: AgentSessionInfo, agentType: String, model: String, timestamp: String` — but `AgentSessionInfo` is not found. Need to check if we should create it or if we need to adjust.

## Test Pattern to Follow

See `AgentUnresponsiveUseCaseImplTest.kt` — uses:
- `AsgardDescribeSpec` base class
- Test fakes (FakeSingleSessionKiller, SpyTmuxCommunicator, AlwaysExistsChecker)
- Helper functions for creating test instances
- BDD structure: GIVEN/WHEN/THEN with one assert per `it` block
