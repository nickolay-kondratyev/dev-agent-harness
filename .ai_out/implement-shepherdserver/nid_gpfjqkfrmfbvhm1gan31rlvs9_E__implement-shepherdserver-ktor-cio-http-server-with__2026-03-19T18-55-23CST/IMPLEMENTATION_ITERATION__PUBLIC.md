# Implementation Iteration: Review Feedback

## Summary

Addressed 4 reviewer suggestions. All accepted with one deviation (item 2).

## Changes Made

### 1. StatusPages for malformed JSON -- ACCEPTED

Installed Ktor `StatusPages` plugin to catch `MissingKotlinParameterException` and return
400 (Bad Request) instead of 500 for malformed JSON payloads with missing required fields.

**Files modified:**
- `gradle/libs.versions.toml` -- added `ktor-server-status-pages` dependency
- `app/build.gradle.kts` -- added `implementation(libs.ktor.server.status.pages)`
- `app/src/main/kotlin/com/glassthought/shepherd/core/server/ShepherdServer.kt` -- installed StatusPages plugin

**Test added:** "WHEN POST /signal/done with missing required field THEN returns 400 (not 500)"

### 2. `compareAndSet` in ack-payload -- DEVIATED

**Deviation:** `compareAndSet` cannot be used here because `PayloadId` is a `value class`.
`AtomicReference` boxes it on each `get()` call, producing distinct object identities.
Since `compareAndSet` uses reference equality (not `.equals()`), it always returns false
for boxed value classes.

**Resolution:** Kept `set(null)` with a WHY-NOT comment explaining why `compareAndSet` is
not applicable. The architecture guarantees single-writer semantics (only
`AckedPayloadSenderImpl` writes non-null values), so the simple set is safe.

### 3. Duplicate self-compacted test -- ACCEPTED

Added two tests:
- "WHEN POST /signal/self-compacted is sent again (duplicate) THEN returns 200 (idempotent)"
- "THEN signalDeferred retains the original SelfCompacted signal"

### 4. Remove unused SIGNAL_ACTION ValType -- ACCEPTED

Removed `ShepherdValType.SIGNAL_ACTION` from `ShepherdValType.kt`. No usages found.

## Test Results

All tests pass (BUILD SUCCESSFUL). 3 new tests added (1 for StatusPages, 2 for duplicate self-compacted).
