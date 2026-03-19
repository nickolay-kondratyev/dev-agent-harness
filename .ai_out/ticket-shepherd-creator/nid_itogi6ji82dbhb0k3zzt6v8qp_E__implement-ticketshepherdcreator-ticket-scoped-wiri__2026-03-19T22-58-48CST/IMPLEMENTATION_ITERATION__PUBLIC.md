# Implementation Iteration -- Review Feedback

## Summary

Addressed 2 accepted IMPORTANT issues and 2 accepted SUGGESTIONS from review. Rejected 3 items as out-of-scope.

## Changes Made

### 1. Added test for blank title validation
- **File**: `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`
- New test: `GIVEN a ticket with blank title > WHEN create() is called > THEN fails with IllegalStateException mentioning 'title'`
- Validates that `VALID_TICKET_DATA.copy(title = "")` throws with clear error message

### 2. Fixed suspend calls in describe blocks
- **File**: `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`
- Moved all `creator.create(...)` calls from `describe` block bodies into `it` blocks
- Affected tests: clean working tree, originating branch, try-N resolution, feature branch, straightforward workflow, with-planning workflow, .ai_out directory structure, CurrentState flush, workflow name forwarding
- Each `it` block now calls `create()` independently, ensuring proper suspend context

### 3. Fixed DIP violation in StateSetupResult
- **File**: `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`
- Changed `StateSetupResult.currentStatePersistence` type from concrete `CurrentStatePersistenceImpl` to `CurrentStatePersistence` interface
- Added explicit import for `CurrentStatePersistence` interface

### 4. Added planning phase assertion in with-planning test
- **File**: `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`
- New test: `THEN CurrentState on disk contains planning phase parts`
- Reads the flushed `current_state.json` and verifies it contains `"phase" : "planning"`

## Rejected Items

| # | Issue | Reason |
|---|-------|--------|
| 3 | `createTestShepherdContext()` duplication | Low-ROI refactoring, out of scope |
| 4 | Same class name in two packages | Normal during transition; old code will be removed |
| 5 | Test fakes duplication | Same as #3, out of scope |
| S3 | Temp directory cleanup | Tests use temp dirs correctly, JVM handles cleanup |
| S4 | `repoRoot` default | Explicit override in tests is acceptable |

## Test Results

All 1363 tests pass (6 skipped integration tests). Total test count increased by 2 (blank title + planning phase assertion).
