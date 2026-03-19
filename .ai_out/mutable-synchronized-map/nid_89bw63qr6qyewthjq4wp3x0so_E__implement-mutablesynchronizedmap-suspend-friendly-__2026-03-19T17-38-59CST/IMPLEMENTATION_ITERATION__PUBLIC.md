# Implementation Iteration: MutableSynchronizedMap Test Fixes

## Changes Made

Addressed review feedback items from `IMPLEMENTATION_REVIEW__PUBLIC.md`.

### 1. Concurrent test now exercises real concurrency (IMPORTANT #1 -- fixed)

**File**: `app/src/test/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMapTest.kt`

Replaced `runTest` (single-threaded `TestCoroutineScheduler`) with `runBlocking` + `Dispatchers.Default`.
Coroutines now launch on the default thread pool, creating real parallelism and actual Mutex contention.

- Removed import: `kotlinx.coroutines.test.runTest`
- Added imports: `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.runBlocking`

### 2. Eliminated shared map instance in "GIVEN empty map" block (Suggestion #1 -- fixed)

Each `it` block under the "GIVEN empty map" `describe` now creates its own `MutableSynchronizedMap` instance,
consistent with all other test blocks in the file. This prevents future shared-state pollution if mutating
tests are added later.

### Not addressed

- **IMPORTANT #2** (removeAll iterator optimization): Review marked this as NIT. The task scope is test-file-only changes. The current implementation is correct and clear.

## Verification

`./gradlew :app:test` -- BUILD SUCCESSFUL. All tests pass.
