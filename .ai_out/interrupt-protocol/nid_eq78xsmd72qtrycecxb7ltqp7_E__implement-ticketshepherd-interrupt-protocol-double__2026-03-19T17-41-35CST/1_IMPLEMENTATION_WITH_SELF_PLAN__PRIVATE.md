# InterruptHandler Implementation — Private Context

## Status: COMPLETE

## What was done
- Created `InterruptHandler` interface + `InterruptHandlerImpl` class
- Created comprehensive BDD test suite with 14 test cases
- All tests pass (both isolated and full suite)

## Key decisions
- Used `handleSignal()` as an internal method for testability — tests call it directly instead of going through JVM signal registration
- Used `java.time.Duration.between().seconds` for the 2-second window check (seconds comparison, so exactly 2 seconds = expired since `< 2`)
- `@Volatile` on `firstPressTimestamp` for thread safety between signal thread and main thread
- Fakes follow the existing pattern from `FailedToExecutePlanUseCaseImplTest`
- `FakeProcessExiter` throws exception to halt execution flow, same pattern as existing tests

## Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/interrupt/InterruptHandler.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/interrupt/InterruptHandlerTest.kt`

## Not done (out of scope)
- Wiring into `TicketShepherdCreator` — the handler needs to be instantiated and `install()` called during startup
- Integration test with actual SIGINT signal delivery
