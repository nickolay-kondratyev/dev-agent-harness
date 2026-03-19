# Implementation Iteration: TicketShepherd Review Fixes

## Changes Made

### 1. CRITICAL: Fixed self-referencing assertion (line 387)

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt`

Changed `capturedActiveExecutors[0] shouldBe capturedActiveExecutors[0]` (compares value to itself -- always passes) to `capturedActiveExecutors[0] shouldNotBe null` which actually verifies the activeExecutor was set during execution.

### 2. IMPORTANT: Added ticket ID to success message

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt`

- Added `ticketId: String` to `TicketShepherdDeps`.
- Changed `SUCCESS_MESSAGE` from a `const` to a `successMessage(ticketId)` function that interpolates the ticket ID.
- Output: `"Workflow completed successfully for ticket {ticketId}."` -- aligning with spec.
- Updated all test fixtures to provide `ticketId = "test-ticket-42"`.
- Updated the success message test to verify the ticket ID is included in the output.

### 3. IMPORTANT: Added missing sub-part configuration test cases

**File:** `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt`

Added two new test blocks:
- **"GIVEN a part with 1 sub-part (doer only)"** -- verifies the factory receives a Part with exactly 1 sub-part.
- **"GIVEN a part with 2 sub-parts (doer + reviewer)"** -- verifies the factory receives a Part with 2 sub-parts and the reviewer is in the second position.

These tests verify the contract that `TicketShepherd` passes the Part structure through to `PartExecutorFactory.create()` faithfully, which is the prerequisite for the factory to create `PartExecutorImpl` with or without `reviewerConfig`.

### 4. NOT fixed (follow-up, as specified)

- `TicketShepherdCreator` wiring -- out of scope.
- Test structure suggestions -- low ROI.

## Test Results

All tests pass: `./gradlew :app:test` exits 0.
