# Implementation Notes (Private)

## Decisions

1. **Mutation approach**: Since `Part` and `SubPart` are immutable data classes, mutations create new copies via `.copy()` and replace entries in the `MutableList<Part>`. This keeps data classes immutable while allowing `CurrentState` to be mutable via its mutable parts list.

2. **Status transition validation**: Added a `validateStatusTransition` companion method on `CurrentState` that validates legal transitions. This is separate from `SubPartStateTransition` (which validates AgentSignal-based transitions) — `CurrentState.updateSubPartStatus` works with raw `SubPartStatus` values, not signals.

3. **`initializePart` on companion**: Exposed as a companion method (not just private) so it can be reused during plan conversion (when execution parts from `plan_flow.json` need the same runtime field initialization).

4. **IOException vs Exception**: Detekt flagged `catch(Exception)` as too generic. Changed to `catch(IOException)` which is the specific exception type for file I/O failures.

## Status
- All implementation complete
- All 67 new tests passing
- Full test suite green
