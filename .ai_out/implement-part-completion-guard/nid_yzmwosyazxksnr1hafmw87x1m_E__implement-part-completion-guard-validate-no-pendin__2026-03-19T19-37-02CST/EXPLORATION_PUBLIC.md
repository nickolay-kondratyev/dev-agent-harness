# Exploration: Part Completion Guard

## Key Files
- **PartExecutorImpl**: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`
  - Guard insertion point: `mapReviewerSignal()` method, PASS branch, after PUBLIC.md validation, before `PartResult.Completed`
- **AiOutputStructure**: `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
  - `feedbackPendingDir(partName)`, `feedbackAddressedDir(partName)` methods available
- **FeedbackResolutionParser**: `app/src/main/kotlin/com/glassthought/shepherd/feedback/FeedbackResolutionParser.kt`
  - Enum: ADDRESSED, REJECTED, SKIPPED
- **PartResult**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/PartResult.kt`
  - Sealed class with: Completed, FailedWorkflow, FailedToConverge, AgentCrashed
- **Tests**: `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (897 lines)
- **Spec**: `doc/plan/granular-feedback-loop.md` — Gate 5 (R8)

## Guard Logic (R8)
After reviewer PASS + PUBLIC.md validation:
1. Check `__feedback/pending/` for `critical__*` and `important__*` files
2. If found → `PartResult.AgentCrashed("Reviewer signaled pass with unaddressed critical/important feedback items in pending/")`
3. Remaining `optional__*` → move to `addressed/` (implicitly accepted as skipped)
4. Return `PartResult.Completed`

## SubPartConfig
- Has `feedbackDir: Path?` field for reviewer config
- `partName` available for path resolution via AiOutputStructure
