# Implementation Iteration: Review Feedback Fixes

## Issues Addressed

### 1. DRY Violation: Duplicated State Machine (IMPORTANT) -- FIXED

**Problem**: `CurrentState.validateStatusTransition` duplicated state machine rules already defined in `SubPartStateTransition`.

**Fix**:
- Removed the entire `companion object` from `CurrentState` (including `validateStatusTransition` and `VALID_FROM_IN_PROGRESS`).
- Added `SubPartStatus.validateTransitionTo(targetStatus)` extension function in `SubPartStateTransition.kt` -- the single source of truth for the state machine.
- `CurrentState.updateSubPartStatus` now calls `currentStatus.validateTransitionTo(newStatus)` directly.
- The `VALID_STATUS_TRANSITIONS` set lives next to the other state machine functions in `SubPartStateTransition.kt`, derived from the sealed class entries.

**Files changed**:
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt` -- removed companion, direct delegation
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartStateTransition.kt` -- added `validateTransitionTo()` and `VALID_STATUS_TRANSITIONS`

### 2. Missing Max Guard on incrementIteration (IMPORTANT) -- FIXED

**Problem**: `incrementIteration` would set `current` beyond `max` without any guard.

**Fix**:
- Added `check(iteration.current < iteration.max)` guard before incrementing.
- Added test: "GIVEN a reviewer sub-part already at max iterations / WHEN incrementing iteration beyond max / THEN throws IllegalStateException"

**Files changed**:
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt` -- added guard
- `app/src/test/kotlin/com/glassthought/shepherd/core/state/CurrentStateMutationTest.kt` -- added test

## Test Results

All tests pass: `./gradlew :app:test` -- EXIT_CODE=0 (including detekt).

## Skipped (per instructions)

The 4 lower-priority suggestions from the review were intentionally not addressed.
