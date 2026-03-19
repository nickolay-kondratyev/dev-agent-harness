# Implementation Iteration - Review Feedback

## Changes Made

### 1. WARN log on duplicate signal callback

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcher.kt`

`CompletableDeferred.complete()` returns `false` when the deferred is already completed. We now capture that return value and log a WARN with `signal_dispatch_duplicate_callback` when it returns `false`, since a duplicate signal is suspicious and worth investigating.

**Test:** Added a `duplicate callback` test block in `SignalCallbackDispatcherTest` that dispatches two signals to the same session and verifies:
- The second dispatch still returns `Success`
- The deferred retains the original signal value
- The test uses `logCheckOverrideAllow(LogLevel.WARN)` — without it, the test framework would fail due to the unexpected WARN log, confirming the WARN is actually emitted.

### 2. Data-driven tests for done result variants

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcherTest.kt`

Replaced three nearly identical test blocks (`done — completed`, `done — pass`, `done — needs_iteration`) with a single data-driven test using `DoneResultTestCase` data class and `forEach`. Each variant now gets its own `describe`/`it` blocks generated from the test case list, covering both `DispatchResult.Success` assertion and `signalDeferred` completion.

## Verification

All tests pass: `./test.sh` exits successfully (BUILD SUCCESSFUL).
