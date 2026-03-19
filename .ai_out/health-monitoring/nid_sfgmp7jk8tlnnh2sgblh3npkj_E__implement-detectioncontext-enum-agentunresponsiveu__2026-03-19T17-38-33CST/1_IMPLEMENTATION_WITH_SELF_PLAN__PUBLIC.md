# Implementation: DetectionContext enum + AgentUnresponsiveUseCase

**Date**: 2026-03-19
**Ticket**: nid_sfgmp7jk8tlnnh2sgblh3npkj_E

## Summary

Implemented the `DetectionContext` enum and `AgentUnresponsiveUseCase` (interface + impl) for the health monitoring feature. The use case consolidates three unresponsive-agent scenarios into a single parameterized class.

## Files Created

### Production Code

1. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/DetectionContext.kt`**
   - Enum with three values: `STARTUP_TIMEOUT`, `NO_ACTIVITY_TIMEOUT`, `PING_TIMEOUT`

2. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt`**
   - `UnresponsiveHandleResult` sealed class (`SessionKilled` / `PingSent`)
   - `SingleSessionKiller` fun interface (testability seam for `TmuxSessionManager.killSession`)
   - `AgentUnresponsiveUseCase` interface
   - `UnresponsiveDiagnostics` data class (handshakeGuid, timeoutDuration, staleDuration)
   - `AgentUnresponsiveUseCaseImpl` implementation

### Test Code

3. **`app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCaseImplTest.kt`**
   - `FakeSingleSessionKiller` — records killed sessions
   - `SpyTmuxCommunicator` — records sendKeys/sendRawKeys calls
   - `AlwaysExistsChecker` — stub for TmuxSession construction
   - 14 test cases covering all three detection contexts, exception bubbling, and diagnostic values

## Design Decisions

### 1. Return type: `UnresponsiveHandleResult` sealed class
The spec states all contexts share "the same outcome" but the NO_ACTIVITY_TIMEOUT context explicitly sends a ping rather than killing. The sealed return type (`SessionKilled` vs `PingSent`) makes this distinction type-safe — the facade's health loop can use exhaustive `when` to handle both cases.

### 2. `SingleSessionKiller` fun interface
`TmuxSessionManager` is a concrete class with infrastructure dependencies (TmuxCommandRunner). Rather than depending on it directly, the use case depends on a `SingleSessionKiller` fun interface. At wiring time, `TmuxSessionManager::killSession` can be passed as the lambda. This follows DIP and enables easy unit testing.

### 3. Health ping mechanism
Since `AckedPayloadSender` is not yet implemented (ref.ap.tbtBcVN2iCl1xfHJthllP.E), the ping uses `TmuxSession.sendRawKeys("Enter")` as a placeholder. A code comment marks this for replacement when `AckedPayloadSender` is available.

### 4. Structured logging
All three detection contexts log via `out.info()` with `Val()` structured values per the spec's logging principle. No string interpolation in log messages. The `Crashed` signal's `details` string uses string concatenation (not logging — this is a diagnostic value passed to the caller).

## Test Results

All tests pass: `./gradlew :app:test` — BUILD SUCCESSFUL.
