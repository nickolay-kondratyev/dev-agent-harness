# PUBLIC: PartExecutorImpl — Review Feedback Iteration

## Summary

Addressed review feedback for PartExecutorImpl. 3 IMPORTANT issues accepted and fixed, 1 IMPORTANT rejected. 1 suggestion (S3) accepted — added test. All other suggestions rejected per plan.

## Changes Made

### I1 ACCEPTED: Lazy reviewer spawn
**Files**: `app/src/main/kotlin/.../core/executor/PartExecutorImpl.kt`

Made reviewer spawning lazy per spec (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E). Reviewer is now spawned **after** doer's first Done(COMPLETED), not eagerly at the start of `executeDoerWithReviewer`. The `reviewerHandle` is nullable (`SpawnedAgentHandle?`) and initialized to `null`. The `mapDoerSignalInReviewerPath` method now accepts nullable reviewer handle, so if the doer fails before COMPLETED, no reviewer session is ever spawned.

Added a new test verifying lazy spawn: when doer signals FailWorkflow, only 1 spawn call is made (doer only).

### I2 ACCEPTED: transitionTo comment
**File**: `app/src/main/kotlin/.../core/executor/PartExecutorImpl.kt`

Added a clarifying comment on the first `transitionTo` call: `// transitionTo called for validation only — throws on invalid state transition.`

### I3 REJECTED: Test duplication
Per CLAUDE.md: "DRY — Most important in business rules. Much less important in tests and boilerplate." Each test is self-contained and readable. No change made.

### I4 ACCEPTED: Semantically specific ValType entries
**Files**: `app/src/main/kotlin/.../core/ShepherdValType.kt`, `app/src/main/kotlin/.../core/executor/PartExecutorImpl.kt`

Added 4 new `ValTypeV2` entries to `ShepherdValType`:
- `ITERATION_COUNT` — current iteration number
- `MAX_ITERATIONS` — maximum iterations allowed
- `SUB_PART_NAME` — name of sub-part being spawned/referenced
- `CONTEXT_WINDOW_REMAINING` — remaining context window percentage

Replaced all `ValType.STRING_USER_AGNOSTIC` usages in `PartExecutorImpl` with the appropriate specific types.

### S3 ACCEPTED: Reviewer Done(COMPLETED) test
**File**: `app/src/test/kotlin/.../core/executor/PartExecutorImplTest.kt`

Added test: "WHEN reviewer signals Done(COMPLETED) instead of PASS or NEEDS_ITERATION THEN IllegalStateException is thrown". This covers the previously untested error path at line 168-170.

### S1, S2, S4 REJECTED
- S1: PartExecutorDeps is pragmatic given orchestration nature.
- S2: PartResult summary strings are not log messages.
- S4: PublicMdValidator as class allows future testability.

## New Tests Added

| Test | Description |
|------|-------------|
| Doer+Reviewer: Reviewer spawned lazily | When doer fails, only 1 spawn call is made (reviewer never spawned) |
| Doer+Reviewer: Reviewer Done(COMPLETED) | Reviewer sending Done(COMPLETED) throws IllegalStateException |

## Test Results

All tests pass. `./test.sh` exits 0. BUILD SUCCESSFUL.
