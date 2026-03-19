# Implementation Iteration: Review Feedback

## Changes Made

### 1. DRY up test setup in SetupPlanUseCaseImplTest

**File:** `app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/SetupPlanUseCaseImplTest.kt`

Extracted duplicate SUT construction (repeated in all 8 `it` blocks) into `lateinit` + `beforeEach` blocks within each `describe("WHEN setup is called")` scope. This follows the same pattern used in `DetailedPlanningUseCaseImplTest`.

Each routing scenario (`GIVEN a straightforward workflow` and `GIVEN a with-planning workflow`) now has its own `beforeEach` that constructs fresh fakes and the SUT, eliminating ~50 lines of boilerplate.

### 2. Narrowed StraightforwardPlanUseCaseImpl constructor

**File:** `app/src/main/kotlin/com/glassthought/shepherd/usecase/planning/StraightforwardPlanUseCase.kt`

Changed constructor from accepting `WorkflowDefinition` to accepting `List<Part>` directly. This:
- Eliminates the `!!` operator from production code
- Makes the precondition (non-null parts) explicit at the type level
- Pushes the `!!` to the wiring boundary where the workflow type is already known
- Removed unused `WorkflowDefinition` import

**File:** `app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/StraightforwardPlanUseCaseImplTest.kt`

Updated test to pass `List<Part>` directly instead of constructing a `WorkflowDefinition`. Removed the now-unnecessary `buildWorkflowDefinition` helper and the `WorkflowDefinition` import.

### 3. Wiring (suggestion #3) -- not addressed (out of scope)

Per instructions, wiring into `TicketShepherdCreator` is deferred.

## Verification

All tests pass: `./gradlew :app:test` exits with code 0.
