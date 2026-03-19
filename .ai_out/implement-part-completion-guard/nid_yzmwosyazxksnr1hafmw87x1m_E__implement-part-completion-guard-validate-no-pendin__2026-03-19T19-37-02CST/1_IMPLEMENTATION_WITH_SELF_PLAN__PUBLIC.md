# Gate 5: Part Completion Guard — Implementation Summary

## What Was Done

Implemented R8 from the granular-feedback-loop spec: a **Part Completion Guard** that validates
no pending critical/important feedback files exist when a reviewer signals PASS.

### New Class: `PartCompletionGuard` (ap.EKFNu5DoQcASJYo4pmgdD.E)
- Located at `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartCompletionGuard.kt`
- Checks `__feedback/pending/` for `critical__*` and `important__*` files
- If blocking files found: returns `GuardResult.Failed` with descriptive message
- If only `optional__*` files remain: moves them to `__feedback/addressed/` and returns `GuardResult.Passed`
- If pending dir is empty or non-existent: returns `GuardResult.Passed`

### Integration into `PartExecutorImpl`
- Added `partCompletionGuard` to `PartExecutorDeps` (default instance)
- In `mapReviewerSignal()` PASS branch: guard runs **after** PUBLIC.md validation, **before** `PartResult.Completed`
- Guard failure maps to `PartResult.AgentCrashed` (reviewer signaling pass with unresolved items = broken agent)

### Tests
- **PartCompletionGuardTest**: 13 test cases covering all scenarios
  - Empty pending → Passed
  - Non-existent pending → Passed
  - Critical in pending → Failed
  - Important in pending → Failed
  - Only optional → Passed + files moved to addressed
  - Mix of optional + critical → Failed (optional NOT moved)
  - Mix of optional + important → Failed
  - Multiple optional files → all moved
  - Multiple blocking files → all listed in message
- **PartExecutorImplTest**: 5 additional test cases for executor integration
  - PASS with empty pending → Completed
  - PASS with critical → AgentCrashed
  - PASS with important → AgentCrashed
  - PASS with only optional → Completed
  - PASS with only optional → files moved from pending to addressed

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartCompletionGuard.kt` (new)
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` (modified)
- `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartCompletionGuardTest.kt` (new)
- `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (modified)
