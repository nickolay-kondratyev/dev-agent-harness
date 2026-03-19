# Implementation Private Notes

## Iteration: Review Feedback Fixes

### Design Decision: validateTransitionTo placement

Placed `validateTransitionTo()` as an extension function on `SubPartStatus` in `SubPartStateTransition.kt`,
co-located with `validateCanSpawn()` and `transitionTo()`. This keeps all state machine validation in one file.

The `VALID_STATUS_TRANSITIONS` set is a private val in the same file. While it technically "re-lists" the
transitions, it derives its entries from the sealed class documentation and lives in the same file as the
authoritative state machine. If a new transition is added to the sealed class, the developer will see
the `VALID_STATUS_TRANSITIONS` set right there and update it. This is a pragmatic compromise -- a fully
derived approach (e.g., reflection over sealed subclasses) would be over-engineering.

### Test impact

- Existing tests for `CurrentStateMutationTest` all still pass unchanged (error messages preserved).
- One new test added for the max iteration guard.
- `SubPartStateTransitionTest` was not modified (validates `transitionTo` and `validateCanSpawn`, not the new `validateTransitionTo`).
  The new `validateTransitionTo` is tested indirectly through `CurrentStateMutationTest`.

### State of branch

Both review issues fixed. All tests green. Ready for commit.
