# Reviewer Private Notes

## Review Process
1. Read all context files (exploration summary, implementation summary, spec)
2. Read all source files (3 production, 4 test)
3. Read related existing code (SubPartStateTransition, SubPart, Part, SubPartStatus)
4. Ran `./sanity_check.sh` - PASS
5. Ran `./test.sh` - PASS (all 67 new tests + existing suite)
6. Compared diff against main (14 files, +1838 lines)
7. Cross-referenced spec lines 428-452 (initialization) and 597-622 (persistence)

## Key Findings

### DRY Violation (IMPORTANT)
`CurrentState.validateStatusTransition` duplicates `SubPartStateTransition` rules. The two implementations
subtly differ on IN_PROGRESS -> IN_PROGRESS (IterateContinue): SubPartStateTransition allows it,
validateStatusTransition rejects it. Works today because updateSubPartStatus is never called for that
path, but this is exactly the kind of silent divergence that causes bugs later.

### Missing Max Guard on incrementIteration (IMPORTANT)
No check that `current < max`. The PartExecutor caller is responsible but this should be enforced
at the mutation layer for defense-in-depth. Easy fix.

### AtomicMoveNotSupportedException
Not handled. Low risk for current deployment (Linux ext4/xfs). Noted as suggestion, not blocking.

### SRP Concern on CurrentState
Went from 1-line data class to ~170 lines with mutations, validation, queries, and helpers.
Acceptable for now but flagged as something to watch. Not blocking.

### Test Quality Assessment
- All 4 test files follow BDD GIVEN/WHEN/THEN with DescribeSpec
- One assert per `it` block consistently
- Good edge case coverage (terminal states, non-existent names, idempotent init)
- Persistence tests cover: round-trip, overwrite, no temp file leaks, null field omission
- Minor: temp directories not cleaned up after tests

### No Existing Tests Removed
Verified: PlanCurrentStateModelTest.kt and SubPartStateTransitionTest.kt are untouched.

### Spec Compliance
- Straightforward init: CORRECT (parts -> EXECUTION phase, runtime fields added)
- With-planning init: CORRECT (planningParts only, execution parts added later)
- Runtime fields: CORRECT (status=NOT_STARTED, iteration.current=0 for reviewers)
- Persistence: CORRECT (uses AiOutputStructure.currentStateJson(), atomic write pattern)
- Flush-after-mutation: Correctly documented as caller responsibility

### Things Not in Scope But Noted
- `appendExecutionParts` does not call `initializePart` on the new parts. The caller is expected
  to initialize them before appending. This is fine if documented clearly but worth verifying
  when the caller (plan conversion) is implemented.
