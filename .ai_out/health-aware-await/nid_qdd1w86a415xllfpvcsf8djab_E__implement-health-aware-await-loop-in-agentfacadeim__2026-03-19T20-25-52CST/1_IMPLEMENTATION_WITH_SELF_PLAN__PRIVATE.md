# Private: Health-Aware Await Loop Implementation

## Status: COMPLETED

All tests pass (`./test.sh` exit 0). Implementation committed.

## What Was Done
- Full health-aware await loop in `AgentFacadeImpl.sendPayloadAndAwaitSignal`
- 3 new constructor dependencies wired
- 4 new ShepherdValType entries
- `QaDrainAndDeliverUseCase` made `open` for testability
- Comprehensive unit tests with virtual time

## Follow-up Items
- `TicketShepherdCreator` needs updating to wire the 3 new deps into `AgentFacadeImpl`
- The `@Suppress("TooManyFunctions")` on `AgentFacadeImpl` could be resolved by extracting `HealthAwareAwaitLoop` to a top-level class

## Test Virtual Time Pattern
Key learning: when using `runTest` + `TestClock`:
1. `advanceTimeBy(1)` first to let the coroutine start and set `lastActivityTimestamp`
2. THEN `clock.advance(...)` to make the timestamp stale
3. THEN `advanceTimeBy(1000)` to trigger the `delay(interval)` in the health loop

Advancing TestClock BEFORE the coroutine starts means `lastActivityTimestamp` gets set to the already-advanced time, making it fresh instead of stale.
