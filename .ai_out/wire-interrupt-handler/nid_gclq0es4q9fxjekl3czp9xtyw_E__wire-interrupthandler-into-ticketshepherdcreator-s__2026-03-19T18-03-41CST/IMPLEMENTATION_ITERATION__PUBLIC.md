# Implementation Iteration: Review Feedback for InterruptHandler Wiring

## Changes Made

### Issue 1 (IMPORTANT): Removed `install()` from `create()`

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherdCreator.kt`

Removed the `interruptHandler.install()` call from `TicketShepherdCreatorImpl.create()`. The creator now performs pure wiring with no side effects. The caller (CLI entry point) is responsible for calling `result.interruptHandler.install()` separately.

- Updated KDoc on both the interface and `create()` method to document this contract.
- No production callers exist yet, so no other files needed updating.

### Issue 2 (IMPORTANT): DRY up test code duplication

**File**: `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`

Moved creator construction to the `describe("WHEN create() is called")` block level. The 4 tests that share the same setup (no need for specific fakes) now share a single creator instance and result. This eliminates 5x repetition of the 8-line construction block down to 1 shared construction + 1 for the test that needs `fakeConsole` access.

### Suggestion 4: Replaced `shouldNotBe null` with meaningful assertion

Replaced the low-value `result.currentStatePersistence shouldNotBe null` (compile-time guaranteed by Kotlin's non-nullable type) with `result.currentStatePersistence.shouldBeInstanceOf<CurrentStatePersistenceImpl>()`. This proves correct wiring -- consistent with how the InterruptHandler test verifies its concrete type.

### Suggestion 5 (REJECTED per plan): Cast to InterruptHandlerImpl

Kept as-is. The cast is justified for proving wiring correctness.

### Suggestion 3 (DEFERRED per plan): FakeShepherdContext

Out of scope. Current approach works fine.

## Test Results

All tests pass: `./gradlew :app:test` -- BUILD SUCCESSFUL.
