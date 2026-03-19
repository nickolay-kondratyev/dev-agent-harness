# SubPartStatus + SubPartStateTransition + PartResult — Implementation Summary

## What Was Implemented

Foundational state machine types for the TICKET_SHEPHERD project, covering sub-part lifecycle
status tracking and transition validation.

### Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartStatus.kt` | Enum with 4 values: NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED |
| `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartStateTransition.kt` | Sealed class with 4 entries (Spawn, Complete, Fail, IterateContinue) + two validator extension functions (`transitionTo`, `validateCanSpawn`) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/state/PartResult.kt` | Sealed class with 4 entries (Completed, FailedWorkflow, FailedToConverge, AgentCrashed) |
| `app/src/test/kotlin/com/glassthought/shepherd/core/state/SubPartStateTransitionTest.kt` | Exhaustive BDD tests for all transitions and validators |

### Design Decisions

- **Validators co-located with SubPartStateTransition**: The `transitionTo()` and `validateCanSpawn()` extension functions live in the same file as SubPartStateTransition since they are tightly coupled — they produce SubPartStateTransition values.
- **No Jackson annotations**: Enum names match JSON values naturally (NOT_STARTED, IN_PROGRESS, etc.).
- **Sealed `when` without `else`**: Compiler enforces exhaustiveness — adding a new status or signal forces all call sites to be updated.
- **Extension functions for validators**: Appropriate here since they are closely coupled to SubPartStatus and produce SubPartStateTransition values.

### Anchor Points

- `ap.EHY557yZ39aJ0lV00gPGF.E` on SubPartStateTransition sealed class (as specified in doc/schema/plan-and-current-state.md)

### Test Results

All tests pass. `./gradlew :app:test` — BUILD SUCCESSFUL.

Test coverage includes:
- All 5 valid transitions from IN_PROGRESS (Done/COMPLETED, Done/PASS, Done/NEEDS_ITERATION, FailWorkflow, Crashed)
- SelfCompacted rejection from IN_PROGRESS
- All signals rejected from NOT_STARTED (data-driven, 6 signals)
- All signals rejected from COMPLETED terminal state (data-driven, 3 signals)
- All signals rejected from FAILED terminal state (data-driven, 3 signals)
- validateCanSpawn success for NOT_STARTED
- validateCanSpawn failure for IN_PROGRESS, COMPLETED, FAILED (data-driven)
