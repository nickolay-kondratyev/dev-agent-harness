# InterruptHandler Implementation — Public Summary

## What was done
Implemented the double Ctrl+C interrupt handler for TicketShepherd per the spec in `doc/core/TicketShepherd.md` (lines 102-121).

### Behavior
1. **First Ctrl+C** — prints "Press Ctrl+C again to confirm exit." in red, records timestamp, execution continues.
2. **Second Ctrl+C within 2 seconds** — kills all TMUX sessions, marks IN_PROGRESS sub-parts as FAILED in CurrentState, flushes state to disk, exits with code 1.
3. **Second Ctrl+C after 2+ seconds** — resets timestamp, reprints message (fresh first press).

## Files created

### Production
- `app/src/main/kotlin/com/glassthought/shepherd/core/interrupt/InterruptHandler.kt`
  - `InterruptHandler` interface with `install()` method
  - `InterruptHandlerImpl` class with constructor-injected dependencies: `Clock`, `AllSessionsKiller`, `CurrentState`, `CurrentStatePersistence`, `ConsoleOutput`, `ProcessExiter`

### Tests
- `app/src/test/kotlin/com/glassthought/shepherd/core/interrupt/InterruptHandlerTest.kt`
  - 14 BDD test cases covering: first press behavior, confirmed double press, expired window reset, boundary timing (1999ms, exactly 2s), mixed sub-part status marking, empty state, multi-press sequences

## Key design decisions
- Uses `sun.misc.Signal` for SIGINT handling (NOT shutdown hooks — they run too late)
- `handleSignal()` is `internal` for direct test invocation without JVM signal wiring
- `runBlocking` in signal callback is acceptable — shutdown-path only
- Only `IN_PROGRESS` sub-parts are transitioned to `FAILED`; `NOT_STARTED`, `COMPLETED`, `FAILED` are untouched
- `@Volatile` on timestamp field for thread safety between signal-dispatch thread and main thread
- 2-second window uses `< 2` comparison on seconds (so exactly 2 seconds = expired)

## Test results
- All 14 new tests pass
- Full `:app:test` suite passes (BUILD SUCCESSFUL)

## Follow-up needed
- Wire `InterruptHandlerImpl` into `TicketShepherdCreator` and call `install()` during startup
