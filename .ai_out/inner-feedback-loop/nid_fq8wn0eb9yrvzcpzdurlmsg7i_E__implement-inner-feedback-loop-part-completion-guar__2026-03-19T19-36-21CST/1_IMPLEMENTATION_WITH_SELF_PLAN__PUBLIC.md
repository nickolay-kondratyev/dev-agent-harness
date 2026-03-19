# Implementation Summary: Inner Feedback Loop in PartExecutorImpl

## What Was Done

Implemented the **Inner Feedback Loop** (R3) in `PartExecutorImpl` with all supporting requirements:

### New Class: InnerFeedbackLoop
- **SRP extraction**: PartExecutorImpl owns the outer loop (doer -> reviewer -> iteration); InnerFeedbackLoop owns the inner loop (per-feedback-item processing within one iteration).
- Processes feedback files in severity order: critical -> important -> optional (sorted by filename within severity).
- For each file: self-compaction check -> re-instruct doer -> validate PUBLIC.md -> read resolution marker -> move file.

### Requirements Implemented
| Requirement | Implementation |
|-------------|---------------|
| **R3**: Inner feedback loop | InnerFeedbackLoop.execute() processes files in severity order, one at a time |
| **R9**: Feedback files presence guard | Empty pending/ -> InnerLoopOutcome.Terminate(AgentCrashed) |
| **R10**: Iteration counter unchanged | currentIteration increments once in processNeedsIteration, not per item |
| **R11**: Harness-owned file movement | ADDRESSED/SKIPPED(optional) -> addressed/, REJECTED+accepted -> rejected/ |
| Resolution marker handling | Missing/invalid -> AgentCrashed, SKIPPED on critical/important -> AgentCrashed |
| Rejection delegation | REJECTED -> RejectionNegotiationUseCase.execute() |
| Self-compaction check | readContextWindowState called before each feedback item |

### Flow Change in PartExecutorImpl
The `executeDoerWithReviewer` method was restructured:
- **Old**: doer COMPLETED -> reviewer signal -> (NEEDS_ITERATION -> re-instruct doer with reviewer PUBLIC.md -> repeat)
- **New**: doer COMPLETED -> reviewer signal -> (NEEDS_ITERATION -> inner feedback loop -> re-instruct reviewer -> repeat)
- When `innerFeedbackLoop` is null (backward compatible), the inner loop is skipped.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt` | New. InnerFeedbackLoop, InnerLoopOutcome, InnerFeedbackLoopDeps, InnerLoopContext |
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` | Added `innerFeedbackLoop` to PartExecutorDeps. Restructured executeDoerWithReviewer and processNeedsIteration. |
| `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` | Added FEEDBACK_FILE_COUNT and FEEDBACK_FILE_NAME val types |
| `app/src/test/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoopTest.kt` | New. 16 BDD tests covering all inner loop scenarios |
| `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` | Updated 5 existing iteration tests for new flow (3 signals instead of 4 when inner loop is null) |

## Tests

**16 new tests** in InnerFeedbackLoopTest:
- Empty pending -> AgentCrashed
- Severity ordering (critical -> important -> optional)
- ADDRESSED -> file moved to addressed/
- SKIPPED on optional -> file moved to addressed/
- SKIPPED on critical -> AgentCrashed
- SKIPPED on important -> AgentCrashed
- Missing resolution marker -> AgentCrashed
- Self-compaction check fires per item
- Iteration counter stays constant per item
- REJECTED + reviewer accepts -> file moved to rejected/
- REJECTED + reviewer insists, doer addresses -> file moved to addressed/
- REJECTED + negotiation crash -> AgentCrashed
- ReInstruct crash -> propagates as AgentCrashed
- ReInstruct FailedWorkflow -> propagates
- Multiple items across severities processed correctly
- Git commit per processed item with FEEDBACK_ADDRESSED result

**5 updated existing tests** in PartExecutorImplTest:
- Signal queues reduced from 4 to 3 (doer re-instruction now handled by inner loop)
- Git commit count, sendPayload count, readContextWindowState count adjusted accordingly

**All tests green.** Full suite: BUILD SUCCESSFUL.

## Decisions

1. **InnerFeedbackLoop is optional** (`innerFeedbackLoop: InnerFeedbackLoop? = null` in PartExecutorDeps) — backward compatible with existing code that does not wire it up yet. The wiring at the TicketShepherdCreator level is a separate concern.

2. **Reused FeedbackFileReader** from `com.glassthought.shepherd.usecase.rejectionnegotiation` package rather than creating a duplicate.

3. **buildFeedbackItemRequest** creates a standard DoerRequest with `reviewerPublicMdPath = null`. The actual per-item instruction content (FeedbackItem section) will be assembled by ContextForAgentProvider based on the request type. This is a simplification for V1 — the context provider already has the FeedbackItem InstructionSection.
