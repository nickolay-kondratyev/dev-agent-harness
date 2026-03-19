# Implementation Review: SpawnTmuxAgentSessionUseCase -- Phase 1 Bootstrap Spawn Flow

## Summary

The implementation introduces `SpawnTmuxAgentSessionUseCase` (ref.ap.M1jzg6RlJkYL4hi8aXr7LnQA.E) which orchestrates Phase 1 of the agent spawn flow: generating a HandshakeGuid, building the TMUX start command via AgentTypeAdapter, creating the TMUX session, awaiting the startup signal, resolving the session ID, and storing a SessionRecord in CurrentState.

**Overall assessment: Solid implementation.** The code is clean, well-structured, follows SRP, and has good test coverage (27 tests). The `TmuxSessionCreator` interface extraction is a good DIP application. Two issues need attention: the exception hierarchy diverges from the project convention, and `serverPort` is a dead parameter.

**Tests pass.** Both `./test.sh` and `./sanity_check.sh` succeed.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Exceptions extend `RuntimeException` instead of `AsgardBaseException`

**Files:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnExceptions.kt`

Both `TmuxSessionCreationException` and `StartupTimeoutException` extend `RuntimeException` directly. Every other custom exception in this project extends `AsgardBaseException`:

- `PayloadAckTimeoutException` at `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/server/PayloadAckTimeoutException.kt` -- extends `AsgardBaseException`
- `PlanConversionException` at `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt` -- extends `AsgardBaseException`
- `ContextWindowStateUnavailableException` at `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/contextwindow/ContextWindowStateUnavailableException.kt` -- extends `AsgardBaseException`

The CLAUDE.md standard says: "Extend `AsgardBaseException` hierarchy for structured exceptions."

`AsgardBaseException` provides structured `Val` logging, `logLevel` control, and `shouldOmitStackTraceFromLogging`. Using `RuntimeException` means the top-level error handler cannot apply these features to spawn exceptions, and the exception does not follow the structured logging pattern (e.g., `sessionName` should be a `Val`, not embedded in the message string).

**Suggested fix:** Extend `AsgardBaseException` and pass `sessionName` as a structured `Val` instead of string-interpolating it into the message.

```kotlin
class TmuxSessionCreationException(
    val sessionName: String,
    cause: Throwable,
) : AsgardBaseException(
    "failed_to_create_tmux_session",
    cause,
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
)
```

**Note on StartupTimeoutException:** `AsgardBaseException` extends `RuntimeException`, NOT `CancellationException`. The `TimeoutCancellationException` cause is preserved correctly as a `cause` field. However, note the project also has `AsgardTimeoutException` (extends `CancellationException`) for timeout cases that need cancellation semantics. Since `StartupTimeoutException` is caught and rethrown (not propagated as cancellation), `AsgardBaseException` is the correct choice here.

### 2. `serverPort` is a dead parameter in `SpawnTmuxAgentSessionParams`

**File:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnTmuxAgentSessionParams.kt` (line 32)

`serverPort` is declared in `SpawnTmuxAgentSessionParams` but never read by `SpawnTmuxAgentSessionUseCase.execute()`. It is not passed to `BuildStartCommandParams`, and `BuildStartCommandParams` does not have a `serverPort` field.

The spec shows that `TICKET_SHEPHERD_SERVER_PORT` is needed in the start command (exported as an env var). Since the port is constant across all sessions, it is correctly treated as adapter-level config (wired once at init time into the adapter constructor, not per-session). But then `serverPort` should not be in `SpawnTmuxAgentSessionParams` at all -- it is dead code that creates confusion.

**Suggested fix:** Remove `serverPort` from `SpawnTmuxAgentSessionParams`. The adapter already receives it at construction time.

---

## Suggestions

### 1. Test DRY opportunity: repeated setup pattern

In the test file at `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/usecase/spawn/SpawnTmuxAgentSessionUseCaseTest.kt`, the pattern of creating a pre-completed deferred + use case + executing is repeated ~18 times:

```kotlin
val startedDeferred = CompletableDeferred<Unit>()
startedDeferred.complete(Unit)
val useCase = createUseCase(outFactory = outFactory)
val result = useCase.execute(createTestParams(startedDeferred))
```

This could be extracted into a helper or a `lateinit var` in the parent `describe` block. However, this is a minor DRY concern in test code (test clarity is more important than test DRY), so it is a suggestion, not an issue.

### 2. `FakeTmuxSessionCreator` uses `Pair` to record calls

At line 58 of the test file:
```kotlin
val createdSessions = mutableListOf<Pair<String, TmuxStartCommand>>()
```

Per CLAUDE.md: "No `Pair`/`Triple` -- create descriptive `data class`." A small `data class CreatedSessionCall(val sessionName: String, val startCommand: TmuxStartCommand)` would be more explicit. This is in test code and minor, but it is a convention deviation.

### 3. Consider using `delay` alternative in async test

Line 538 uses `kotlinx.coroutines.delay(50.milliseconds)` inside a `launch` block to simulate async deferred completion. While this is modeling real behavior (not synchronization), it makes the test timing-dependent. With `runTest` (Kotest's coroutine test support), `advanceTimeBy` could be used instead for deterministic behavior. This is a minor robustness improvement.

---

## Documentation Updates Needed

None required. The spec at `doc/use-case/SpawnTmuxAgentSessionUseCase.md` already has the `Implemented in: SpawnTmuxAgentSessionUseCase (ref.ap.M1jzg6RlJkYL4hi8aXr7LnQA.E)` reference. The anchor point is correctly applied.

---

## Spec Compliance Check

| Spec Step | Implemented | Notes |
|-----------|-------------|-------|
| Generate HandshakeGuid | YES | `HandshakeGuid.generate()` |
| Build TMUX session name `shepherd_${partName}_${subPartName}` | YES | `buildSessionName()` |
| Build start command via AgentTypeAdapter | YES | `adapter.buildStartCommand(BuildStartCommandParams(...))` |
| Create TMUX session | YES | `tmuxSessionCreator.createSession()` |
| Await `/signal/started` within `healthTimeouts.startup` | YES | `withTimeout(healthTimeoutLadder.startup)` |
| Resolve session ID | YES | `adapter.resolveSessionId(handshakeGuid)` |
| Store SessionRecord in CurrentState | YES | `currentState.addSessionRecord()` |
| Return TmuxAgentSession | YES | Correct composition of `tmuxSession` + `resumableAgentSessionId` |
| TMUX creation failure -> exception | YES | `TmuxSessionCreationException` wraps `IllegalStateException` |
| Startup timeout -> exception | YES | `StartupTimeoutException` wraps `TimeoutCancellationException` |
| Missing adapter -> IllegalArgumentException | YES | `requireAdapter()` |

The implementation is faithful to the spec.
