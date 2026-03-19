# Implementation Iteration: Inject Conversion Errors into Retry Context

## Review Issue Addressed
**BLOCKING**: `PlanConversionException.message` was logged but never passed to the next planning executor on retry, causing the planner to retry blindly without knowledge of prior validation errors.

## Changes Made

### 1. `PlanningPartExecutorFactory` (interface update)
- Changed from `fun interface` to `interface` (Kotlin `fun interface` does not support default parameter values).
- Added `priorConversionErrors: List<String> = emptyList()` parameter to `create()`.
- Added KDoc explaining the parameter's purpose.

### 2. `DetailedPlanningUseCaseImpl` (error accumulation)
- Added `conversionErrors` mutable list that accumulates error messages across retry attempts.
- Each `PlanConversionException` catch block appends `e.message` to the list.
- `partExecutorFactory.create()` now receives `conversionErrors.toList()` (defensive copy) on each iteration.

### 3. `DetailedPlanningUseCaseImplTest` (test updates)
- Updated `FakePlanningPartExecutorFactory` to accept and record `priorConversionErrors` via `createCallErrors` list.
- **Hoisted shared setup** into `beforeEach` blocks for the retry describe blocks (reducing duplication).
- Added new test cases:
  - First `create()` call receives empty `priorConversionErrors`
  - Retry `create()` call receives the error message from the failed conversion
  - Error accumulation across multiple retries (3 attempts, third call gets both prior errors)
  - Happy path verification that first call gets empty errors

## Design Decisions
- **`interface` instead of `fun interface`**: Required because Kotlin functional interfaces cannot have methods with default parameter values. This is a minor change -- the interface was only implemented by fakes in tests and will be implemented by production wiring code later.
- **Defensive copy via `toList()`**: Prevents the executor from seeing mutations to the error list that happen after its creation.
- **Error accumulation (not replacement)**: All prior errors are passed, not just the most recent one. This gives the planner full context of what went wrong across all attempts.

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/PlanningPartExecutorFactory.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCase.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/DetailedPlanningUseCaseImplTest.kt`

## Test Results
All tests pass (`./test.sh` exit code 0).
