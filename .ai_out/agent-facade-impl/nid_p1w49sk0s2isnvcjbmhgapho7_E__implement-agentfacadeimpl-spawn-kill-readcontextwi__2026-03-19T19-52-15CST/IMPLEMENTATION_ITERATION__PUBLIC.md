# AgentFacadeImpl Review Feedback — Iteration

## Summary

Addressed review feedback for `AgentFacadeImpl`. Fixed 1 critical bug (C1), documented 1 V1 limitation (C2), fixed 3 important issues (I1, I2, I4), and rejected 1 (I5).

## Changes Applied

### C1 (CRITICAL): Fixed stale entry reference in `sendPayloadAndAwaitSignal`
Changed `existingEntry.tmuxAgentSession.tmuxSession.sendKeys(...)` to `updatedEntry.tmuxAgentSession.tmuxSession.sendKeys(...)`. The code was sending via the old entry reference after registering the updated one.

### C2 (CRITICAL): Documented V1 stub ACK protocol bypass
Updated KDoc on `sendPayloadAndAwaitSignal` to clearly state the V1 limitation: raw instruction file path is sent without ACK protocol wrapping (ref.ap.tbtBcVN2iCl1xfHJthllP.E). Full ACK-wrapped delivery via `AckedPayloadSender` deferred to ticket nid_qdd1w86a415xllfpvcsf8djab_E.

### I1: `AgentSpawnException` now extends `AsgardBaseException`
Changed from `RuntimeException` to `AsgardBaseException` with structured `Val` parameters (`sessionName`, `timeout`), following the pattern in `SpawnExceptions.kt`.

### I2: `questionQueue` contents preserved
When creating the updated `SessionEntry` in `sendPayloadAndAwaitSignal`, the existing `questionQueue` reference is now passed through instead of creating a new empty queue.

### I3: `withFreshDeferred` eliminated
Inlined the `SessionEntry` construction directly in `sendPayloadAndAwaitSignal`. This also resolved a detekt `TooManyFunctions` violation that would have occurred if the method was moved into the class.

### I4: Removed `delay` from tests
Replaced two `delay`-based synchronization patterns:
1. **spawn helper**: Replaced polling loop with `FakeCreator.onSessionCreated` callback that completes the startup deferred synchronously when `createSession` is called.
2. **sendPayloadAndAwaitSignal test**: Replaced `delay(50ms)` + `launch` with `SignalCompletingCommunicator` that completes the signal deferred when `sendKeys` is called.

### I5 (REJECTED): `System.getProperty("user.dir")` hardcoded
Rejected as over-engineering for V1. The working directory configuration will be addressed when `SpawnAgentConfig` is enhanced.

## Files Modified

- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImplTest.kt`

## Tests

All `:app:test` tests pass (20 unit tests for AgentFacadeImpl + all existing tests).
