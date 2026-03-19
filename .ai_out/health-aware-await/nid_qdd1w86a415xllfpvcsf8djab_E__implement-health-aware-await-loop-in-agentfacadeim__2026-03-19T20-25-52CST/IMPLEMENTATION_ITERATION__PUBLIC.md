# Implementation Iteration 1: Review Fixes

## Status: COMPLETED

All tests pass (`./test.sh` exit 0).

## Changes Made

### Issue 1: `checkStaleness()` now asserts `PingSent` result

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`

The return value of `agentUnresponsiveUseCase.handle()` was being discarded in `checkStaleness()`. Added a `check()` assertion to make the spec assumption explicit: `NO_ACTIVITY_TIMEOUT` must always produce `PingSent`. If this invariant is ever violated, the `check()` will fail fast with a clear message instead of silently proceeding to the ping-wait phase.

### Issue 2: Extracted `fun interface QaDrainer` (DIP compliance)

**Files**:
- `app/src/main/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCase.kt` -- Added `fun interface QaDrainer`, `QaDrainAndDeliverUseCase` now implements it, removed `open` from class and method.
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt` -- Constructor parameter changed from `QaDrainAndDeliverUseCase` to `QaDrainer`.
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImplTest.kt` -- `QaDrainTracker` now implements `QaDrainer` directly instead of extending `QaDrainAndDeliverUseCase`. Simplified from anonymous subclass with fake deps to a clean interface implementation.

## Rejected Suggestions (per plan)

- Timestamp precision in `awaitPingProofOfLife` -- not a correctness issue
- Negative duration guard in `checkStaleness` -- handled correctly by existing logic
- Test helper extraction -- non-blocking style preference
