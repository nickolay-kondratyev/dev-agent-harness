# Implementation Iteration - Private State

## What Was Done
Fixed 2 IMPORTANT issues from review + 1 nice-to-have suggestion.

### Issue 1: Reviewer PASS path ignores CompactionOutcome
- **File**: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`
- **Lines ~233-253**: Changed `DoneResult.PASS` branch in `mapReviewerSignal` to capture and check `afterDone()` return value
- **Pattern**: Same `when(compactionOutcome)` pattern used in `mapDoerSignalInReviewerPath` and `processNeedsIteration`
- Added `NestedBlockDepth` to the existing `@Suppress` annotation on `mapReviewerSignal` (needed due to additional nesting)

### Issue 2: Crashed signal during compaction does not kill session
- **File**: Same file, `performCompaction` method, `AgentSignal.Crashed` branch (~line 398)
- Added `deps.agentFacade.killSession(handle)` before returning `CompactionFailed`

### Suggestion 3: Temp file cleanup (implemented)
- Added `try/finally` around `sendPayloadAndAwaitSignal` in `performCompaction` to delete temp instruction file

### Suggestions 4-5: Skipped
- Suggestion 4 (`@Suppress("UnusedParameter")`): Acceptable for V1, no change needed
- Suggestion 5 (verify PRIVATE.md in respawned instructions): Low ROI, implicitly tested

## Tests Added
1. `GIVEN a doer+reviewer executor where reviewer has low context / WHEN reviewer signals PASS but compaction fails (missing PRIVATE.md) / THEN the result is AgentCrashed, not Completed`
2. `GIVEN a doer-only executor with low context / WHEN agent crashes during compaction / THEN killSession is called for the crashed session`

## Test Count
44 tests total (was 42), all passing.
