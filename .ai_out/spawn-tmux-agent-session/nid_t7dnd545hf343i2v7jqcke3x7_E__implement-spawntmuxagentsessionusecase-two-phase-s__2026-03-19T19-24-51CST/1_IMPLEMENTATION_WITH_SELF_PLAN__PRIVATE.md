# Private: SpawnTmuxAgentSessionUseCase Implementation Notes

## Status: COMPLETE

## Key Implementation Details

### Use Case Location
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnTmuxAgentSessionUseCase.kt`

### Dependencies (Constructor Injection)
- `agentTypeAdapters: Map<AgentType, AgentTypeAdapter>` — dispatch by agentType
- `tmuxSessionCreator: TmuxSessionCreator` — interface (not TmuxSessionManager directly)
- `healthTimeoutLadder: HealthTimeoutLadder` — for startup timeout
- `currentState: CurrentState` — for storing session record
- `clock: Clock` — `com.glassthought.shepherd.core.time.Clock` (fun interface)
- `outFactory: OutFactory` — for structured logging

### Interface Extraction
- Created `TmuxSessionCreator` interface at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionCreator.kt`
- `TmuxSessionManager` now implements both `SessionExistenceChecker` and `TmuxSessionCreator`
- `createSession` in `TmuxSessionManager` now has `override` keyword

### Error Handling
- TMUX creation: `IllegalStateException` → wrapped in `TmuxSessionCreationException`
- Startup timeout: `TimeoutCancellationException` → wrapped in `StartupTimeoutException` (with cause preserved for detekt compliance)

### Logging
- Uses `ShepherdValType.HANDSHAKE_GUID` for handshakeGuid values
- Uses `ValType.STRING_USER_AGNOSTIC` for session name and agent type
- Two log points: `spawning_agent_session` (before) and `agent_session_spawned` (after)

### Test Fakes
- `FakeAgentTypeAdapter` — records calls, returns configurable resolved session ID
- `FakeTmuxSessionCreator` — records calls, returns test TmuxSession
- `FailingTmuxSessionCreator` — throws IllegalStateException
- `FixedClock` — deterministic time for session record timestamps

### What Remains for Phase 2
- Phase 2 (work instructions delivery) is not part of this implementation
- Phase 2 would use `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E) to send instruction file path via TMUX send-keys
- The returned `TmuxAgentSession` has the `tmuxSession` handle needed for Phase 2 send-keys
