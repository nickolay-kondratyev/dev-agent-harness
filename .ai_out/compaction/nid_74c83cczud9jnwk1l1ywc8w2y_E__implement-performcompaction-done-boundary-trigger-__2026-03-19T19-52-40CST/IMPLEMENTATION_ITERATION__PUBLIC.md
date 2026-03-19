# Implementation Iteration - Review Fixes

## Summary
Fixed both IMPORTANT issues from review. All 44 tests pass (2 new tests added).

## MUST-FIX Issues Resolved

### Issue 1: Reviewer PASS path now checks CompactionOutcome
**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`

The `DoneResult.PASS` branch in `mapReviewerSignal` previously discarded `afterDone()` return value. Now captures `CompactionOutcome` and maps `CompactionFailed` to `AgentCrashed`, consistent with all other call sites.

### Issue 2: Crashed signal during compaction now kills session
**File**: Same file, `performCompaction` method.

Added `deps.agentFacade.killSession(handle)` to the `AgentSignal.Crashed` branch, preventing lingering TMUX sessions. Now consistent with `Done` and `FailWorkflow` branches.

## Nice-to-Have Suggestions

| # | Suggestion | Decision | Rationale |
|---|-----------|----------|-----------|
| 3 | Temp file leak in performCompaction | **Implemented** | Easy fix via try/finally |
| 4 | @Suppress("UnusedParameter") on trigger | **Skipped** | Acceptable for V1 per reviewer |
| 5 | Verify PRIVATE.md in respawned instructions | **Skipped** | Low ROI, implicitly tested |

## Tests Added
1. Reviewer PASS + compaction failure (missing PRIVATE.md) returns `AgentCrashed`
2. Crashed signal during compaction calls `killSession` (verified via `FakeAgentFacade.killSessionCalls`)

## Test Results
44 tests, 0 failures, BUILD SUCCESSFUL.
