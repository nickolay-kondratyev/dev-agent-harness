# Implementation: Health-Aware Await Loop in AgentFacadeImpl

## Summary

Replaced the V1 stub `sendPayloadAndAwaitSignal` with a full health-aware await loop per spec ref.ap.QCjutDexa2UBDaKB3jTcF.E.

## Changes Made

### AgentFacadeImpl.kt
- Added 3 new constructor dependencies: `AckedPayloadSender`, `AgentUnresponsiveUseCase`, `QaDrainAndDeliverUseCase`
- Replaced V1 stub (raw send-keys + simple await) with full health-aware loop
- Extracted `HealthAwareAwaitLoop` as a private inner class for readability
- Extracted `deliverPayloadOrCrash` for ACK-wrapped delivery with crash handling
- Added `@Suppress` for `LongParameterList` (11 deps justified by facade role) and `TooManyFunctions`

### Health-Aware Loop Flow
1. Create fresh deferred, re-register session entry
2. Deliver payload via `AckedPayloadSender.sendAndAwaitAck` — catch `PayloadAckTimeoutException` -> kill session -> return `AgentSignal.Crashed`
3. Health monitoring loop polling every `healthCheckInterval`:
   - Check `signalDeferred.isCompleted` -> return signal
   - Check `isQAPending` -> drain Q&A via `QaDrainAndDeliverUseCase`, skip health checks
   - Check `lastActivityTimestamp` staleness vs `normalActivity` threshold
   - If stale: `AgentUnresponsiveUseCase(NO_ACTIVITY_TIMEOUT)` -> PingSent
   - Wait `pingResponse` window, check for proof of life (any callback advancing timestamp)
   - If still stale: `AgentUnresponsiveUseCase(PING_TIMEOUT)` -> SessionKilled -> return Crashed

### ShepherdValType.kt
- Added `CALLBACK_AGE`, `TIMEOUT_THRESHOLD`, `STALE_DURATION`, `TMUX_SESSION_NAME`

### QaDrainAndDeliverUseCase.kt
- Made class and `drainAndDeliver` method `open` to enable test overriding

### Tests (AgentFacadeImplTest.kt)
- Updated test harness to wire 3 new dependencies with fakes
- Added fakes: `FakeAckedPayloadSender`, `FakeAgentUnresponsiveUseCase`, `QaDrainTracker`
- Added comprehensive tests for all health monitoring branches:
  - Signal arrives before timeout (healthy path)
  - Fresh activity timestamp (no action)
  - Stale timestamp -> NO_ACTIVITY_TIMEOUT ping sent
  - Callback during ping window -> agent alive, loop continues
  - No callback after ping window -> PING_TIMEOUT -> session killed -> Crashed
  - Q&A pending -> health checks skipped, Q&A drained
  - Q&A completes -> health checks resume
  - PayloadAckTimeoutException -> Crashed
  - FailWorkflow signal
- Tests use `kotlinx-coroutines-test` (`runTest`, `advanceTimeBy`) + `TestClock` for virtual time

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCase.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImplTest.kt`

## Design Decisions
- Used `inner class HealthAwareAwaitLoop` to keep individual methods short (satisfy detekt) while maintaining access to facade's fields
- Used `info` (not `warn`) for payload ACK timeout crash since it's an expected code path, not an unexpected error
- Made `QaDrainAndDeliverUseCase.drainAndDeliver` `open` rather than extracting an interface — minimal change for testability
- Java Duration -> Kotlin Duration conversion via `toMillis()` + `.milliseconds` (clean, no ISO parsing)

## Concerns
- `TicketShepherdCreator` wiring needs updating to pass the 3 new deps (separate ticket/task)
- The `TooManyFunctions` suppress on `AgentFacadeImpl` may warrant future refactoring to extract the health loop logic
