# Implementation: SpawnTmuxAgentSessionUseCase — Phase 1 Bootstrap Spawn Flow

## What Was Done

Implemented `SpawnTmuxAgentSessionUseCase` (ref.ap.M1jzg6RlJkYL4hi8aXr7LnQA.E) — the Phase 1 bootstrap spawn flow that orchestrates creating a TMUX agent session, performing the startup handshake, resolving the agent session ID, and recording the session.

### Implementation Summary

1. **`SpawnTmuxAgentSessionParams`** — data class bundling all per-spawn parameters (partName, subPartName, agentType, model, workingDir, tools, systemPromptFilePath, appendSystemPrompt, bootstrapMessage, serverPort, startedDeferred).

2. **`SpawnTmuxAgentSessionUseCase`** — main use case class with `suspend fun execute(params)`:
   - Generates `HandshakeGuid` via `HandshakeGuid.generate()`
   - Builds TMUX session name: `shepherd_${partName}_${subPartName}`
   - Builds start command via `AgentTypeAdapter.buildStartCommand(BuildStartCommandParams)`
   - Creates TMUX session via `TmuxSessionCreator.createSession()`
   - Awaits `startedDeferred` within `healthTimeoutLadder.startup` using `withTimeout`
   - Resolves session ID via `adapter.resolveSessionId(handshakeGuid)`
   - Stores `SessionRecord` in `CurrentState.addSessionRecord()`
   - Returns `TmuxAgentSession(tmuxSession, resumableAgentSessionId)`

3. **Custom exceptions**:
   - `TmuxSessionCreationException` — wraps `IllegalStateException` from TMUX creation failure
   - `StartupTimeoutException` — wraps `TimeoutCancellationException` from startup timeout

4. **`TmuxSessionCreator` interface** — extracted from `TmuxSessionManager` for testability (DIP). `TmuxSessionManager` now implements `TmuxSessionCreator`.

5. **27 unit tests** — all passing, covering:
   - Happy path (session name, pane target, agentType, sessionId, model, handshakeGuid)
   - Session record storage (agentType, model, timestamp, sessionId, handshakeGuid)
   - TMUX creation failure (exception type, session name, cause wrapping)
   - Startup timeout (exception type, session name, timeout duration)
   - Session name format verification
   - BuildStartCommandParams construction verification
   - Missing adapter (IllegalArgumentException for unregistered AgentType)
   - Async deferred completion

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnTmuxAgentSessionUseCase.kt` | Main use case implementation |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnTmuxAgentSessionParams.kt` | Input params data class |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnExceptions.kt` | TmuxSessionCreationException, StartupTimeoutException |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionCreator.kt` | Interface extracted for testability |
| `app/src/test/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnTmuxAgentSessionUseCaseTest.kt` | 27 unit tests |

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt` | Added `TmuxSessionCreator` to implements list, added `override` to `createSession` |

## Decisions Made

1. **Extracted `TmuxSessionCreator` interface** — `TmuxSessionManager` is a class (not interface), so direct dependency would prevent unit testing. Follows the existing pattern (`SingleSessionKiller`, `SessionExistenceChecker`).

2. **`serverPort` in params** — included as instructed but not passed to `BuildStartCommandParams` (which doesn't have it). The server port is adapter-level config wired at init time.

3. **Exception hierarchy** — both custom exceptions extend `RuntimeException` (not checked). `StartupTimeoutException` preserves the `TimeoutCancellationException` as cause to satisfy detekt's `SwallowedException` rule.

## Test Results

- 27 tests, 0 failures, 0 skipped
- All existing tests continue to pass
- Build completed successfully (compile + detekt + test)
