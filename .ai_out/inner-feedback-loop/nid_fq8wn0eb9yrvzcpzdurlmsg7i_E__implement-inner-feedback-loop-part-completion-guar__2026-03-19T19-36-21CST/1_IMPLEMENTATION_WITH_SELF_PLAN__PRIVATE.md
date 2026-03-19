# Implementation Private Context

## Status: COMPLETE

## What Was Done
Implemented the InnerFeedbackLoop class and integrated it into PartExecutorImpl.

## Key Design Decisions

### 1. SRP: InnerFeedbackLoop as separate class
- PartExecutorImpl owns the outer loop (doer -> reviewer -> iteration)
- InnerFeedbackLoop owns the inner loop (per-feedback-item processing)
- Clean boundary via InnerLoopOutcome sealed class (Continue/Terminate)

### 2. Dependencies pattern: InnerFeedbackLoopDeps + InnerLoopContext
- InnerFeedbackLoopDeps bundles constructor-injected collaborators (8 deps -> 1 data class)
- InnerLoopContext bundles per-invocation parameters (6 params -> 1 data class)
- Both needed to satisfy detekt LongParameterList threshold

### 3. Optional InnerFeedbackLoop in PartExecutorDeps
- `innerFeedbackLoop: InnerFeedbackLoop? = null` in PartExecutorDeps
- When null, processNeedsIteration skips the inner loop (backward compatible)
- Existing tests pass without providing InnerFeedbackLoop

### 4. Flow restructuring in executeDoerWithReviewer
- Old flow: doer -> reviewer -> (NEEDS_ITERATION -> re-instruct doer -> repeat)
- New flow: doer -> reviewer -> (NEEDS_ITERATION -> inner loop -> re-instruct reviewer -> repeat)
- mapReviewerSignal for NEEDS_ITERATION now returns null (was calling processNeedsIteration)
- The outer while loop calls processNeedsIteration then sendReviewerInstructions

### 5. Reusing FeedbackFileReader from rejectionnegotiation package
- Did NOT create a duplicate FeedbackFileReader
- Reused the existing one from com.glassthought.shepherd.usecase.rejectionnegotiation

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt` (new)
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` (modified)
- `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` (added 2 val types)
- `app/src/test/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoopTest.kt` (new)
- `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (updated)

## Test Results
- All 26 existing PartExecutorImplTest tests pass (5 updated for new flow)
- 16 new InnerFeedbackLoopTest tests pass
- Full suite green
