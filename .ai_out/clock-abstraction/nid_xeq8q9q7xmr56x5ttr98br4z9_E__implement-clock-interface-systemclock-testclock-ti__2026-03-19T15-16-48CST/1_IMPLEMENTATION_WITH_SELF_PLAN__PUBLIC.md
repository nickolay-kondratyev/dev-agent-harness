# Clock Abstraction Implementation

## What Was Done

Implemented a `Clock` interface with `SystemClock` (production) and `TestClock` (test double) to enable deterministic time control in tests.

### Files Created

1. **`app/src/main/kotlin/com/glassthought/shepherd/core/time/Clock.kt`**
   - `Clock` interface with `fun now(): Instant`
   - `SystemClock` class — production implementation backed by `Instant.now()`
   - KDoc references `ref.ap.whDS8M5aD2iggmIjDIgV9.E` (Virtual Time Strategy spec)
   - Anchor point: `ap.xR4kT7vNcW9pLmQjY2bFs.E`

2. **`app/src/test/kotlin/com/glassthought/shepherd/core/time/TestClock.kt`**
   - Deterministic `Clock` for tests (NOT thread-safe)
   - Constructor takes optional `initialTime: Instant` (defaults to `Instant.EPOCH`)
   - `advance(duration: Duration)` — advances internal time using `kotlin.time.Duration`
   - `set(instant: Instant)` — sets internal time directly

3. **`app/src/test/kotlin/com/glassthought/shepherd/core/time/ClockTest.kt`**
   - 7 test cases, all passing
   - BDD style with `AsgardDescribeSpec`, one assert per `it` block
   - Covers: SystemClock proximity check, TestClock default/custom initial time, advance, cumulative advance, set, set-after-advance

## Design Decisions

- Followed `DispatcherProvider` pattern for interface style (regular interface, not `fun interface`, since future consumers may need additional methods)
- `TestClock` placed in test sources since it is a test double, not production code
- Used `kotlin.time.Duration` for `advance()` parameter with `toJavaDuration()` conversion for `Instant` math, consistent with existing `HarnessTimeoutConfig` patterns

## Test Results

All 7 tests pass. Full `:app:test` suite passes (EXIT=0).
