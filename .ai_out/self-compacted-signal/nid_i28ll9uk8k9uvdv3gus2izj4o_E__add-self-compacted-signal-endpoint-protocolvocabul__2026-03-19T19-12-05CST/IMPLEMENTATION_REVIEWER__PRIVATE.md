# IMPLEMENTATION_REVIEWER Private State

## Review Session: 2026-03-19

### Files Reviewed
- `app/src/main/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcher.kt` (NEW)
- `app/src/test/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcherTest.kt` (NEW)
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` (MODIFIED: val -> @Volatile var)
- `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` (MODIFIED: added HANDSHAKE_GUID, SIGNAL_ACTION)

### Test Results
- `./sanity_check.sh` -- PASS
- `./test.sh` -- PASS (BUILD SUCCESSFUL)

### Analysis Notes

#### Thread Safety of timestamp + deferred completion
- `lastActivityTimestamp` is `@Volatile var` -- single-writer pattern (only dispatcher writes).
- `signalDeferred.complete()` is thread-safe by kotlinx.coroutines design (returns false if already completed).
- Ordering: timestamp updated BEFORE deferred completion. This is correct -- any code that reads the timestamp after awaiting the deferred will see the updated value.
- `CompletableDeferred.complete()` returns Boolean. The return value is ignored. If called twice, second call silently returns false. This is benign for the dispatcher -- if duplicate HTTP callbacks arrive, the second one will still log "signal_dispatched" even though the deferred was already completed. This is a minor issue but not critical.

#### SessionEntry val -> @Volatile var
- Follows exact same pattern as `SpawnedAgentHandle.lastActivityTimestamp` which is also `@Volatile var`.
- Checked all usages of `lastActivityTimestamp` on SessionEntry -- only read by health monitoring (DetectionContext) and written by dispatcher.
- Single-writer pattern is safe with @Volatile.

#### Missing `else` branch in when(signalOrError)
- `when (signalOrError)` on DispatchResult is exhaustive because it's a sealed class. Correct.

#### Test coverage assessment
- 15 test cases covering: all signal types, all done result variants, session not found, bad request cases (missing fields, invalid values, unknown action).
- BDD structure with GIVEN/WHEN/THEN.
- Uses one-assert-per-test pattern correctly.
- Clock injection for deterministic time assertions.
- Uses `createTestSessionEntry()` fixture.

#### Potential issue: log message string embeds value
- Line 45: `"missing_field: $FIELD_HANDSHAKE_GUID"` -- this embeds a value in the message string, which goes against structured logging. BUT this is a BadRequest message returned to the caller, not a log message. It's the `message` field of `DispatchResult.BadRequest`. This is fine.
