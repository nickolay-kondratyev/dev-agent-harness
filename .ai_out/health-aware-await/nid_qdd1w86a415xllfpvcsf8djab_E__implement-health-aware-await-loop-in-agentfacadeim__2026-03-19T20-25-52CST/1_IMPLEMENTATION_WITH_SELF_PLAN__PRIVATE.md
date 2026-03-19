# Private: Health-Aware Await Loop Implementation

## Status: COMPLETED (iteration 1 review fixes applied)

All tests pass (`./test.sh` exit 0).

## What Was Done (iteration 0)
- Full health-aware await loop in `AgentFacadeImpl.sendPayloadAndAwaitSignal`
- 3 new constructor dependencies wired
- 4 new ShepherdValType entries
- Comprehensive unit tests with virtual time

## Iteration 1 Review Fixes
- **checkStaleness()**: Added `check(result is UnresponsiveHandleResult.PingSent)` to assert the spec invariant that `NO_ACTIVITY_TIMEOUT` always produces `PingSent`.
- **QaDrainer interface**: Extracted `fun interface QaDrainer` in `QaDrainAndDeliverUseCase.kt`. `AgentFacadeImpl` now depends on the interface. Test fake `QaDrainTracker` implements `QaDrainer` directly. Removed `open` from class and method.

## Follow-up Items
- `TicketShepherdCreator` needs updating to wire the 3 new deps into `AgentFacadeImpl`
- The `@Suppress("TooManyFunctions")` on `AgentFacadeImpl` could be resolved by extracting `HealthAwareAwaitLoop` to a top-level class

## Test Virtual Time Pattern
Key learning: when using `runTest` + `TestClock`:
1. `advanceTimeBy(1)` first to let the coroutine start and set `lastActivityTimestamp`
2. THEN `clock.advance(...)` to make the timestamp stale
3. THEN `advanceTimeBy(1000)` to trigger the `delay(interval)` in the health loop

Advancing TestClock BEFORE the coroutine starts means `lastActivityTimestamp` gets set to the already-advanced time, making it fresh instead of stale.
