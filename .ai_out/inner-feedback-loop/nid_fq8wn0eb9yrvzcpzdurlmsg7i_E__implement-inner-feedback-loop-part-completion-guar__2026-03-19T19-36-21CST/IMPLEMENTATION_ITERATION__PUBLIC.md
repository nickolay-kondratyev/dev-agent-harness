# Implementation Iteration: Review Feedback

## Issues Addressed

### I1: `sortBySeverity` silently drops files with unrecognized severity prefixes (FIXED)

**Problem:** Files in `pending/` with typos in severity prefix (e.g., `critcal__foo.md`) were silently dropped by `sortBySeverity`, which only collected files matching the three known prefixes. The bug guard at the end of the loop also missed these files since it only checks for `critical__*` and `important__*`.

**Fix:** Added pre-loop validation in `InnerFeedbackLoop.execute()`:
- New companion method `hasRecognizedSeverityPrefix(file)` checks if a file starts with `critical__`, `important__`, or `optional__`.
- New companion method `findUnrecognizedPrefixFiles(files)` filters files that lack a recognized prefix.
- Before sorting, `execute()` calls `findUnrecognizedPrefixFiles(pendingFiles)` and returns `InnerLoopOutcome.Terminate(PartResult.AgentCrashed(...))` if any are found.
- Error message includes the unrecognized filenames for diagnostic clarity.

**Tests added:** 2 new tests in `InnerFeedbackLoopTest`:
1. File with typo prefix (`critcal__typo-issue.md`) results in `Terminate(AgentCrashed)`.
2. Error message contains the unrecognized filename.

### I2: `buildFeedbackItemRequest` does not pass feedback content (REJECTED)

This is the ContextForAgentProvider integration (Gate 2/R4 from the spec) which is tracked separately. The inner loop orchestration is correct; the doer instruction assembly is a separate concern.

### I3: No PartExecutorImpl integration test with inner loop wired (FIXED)

**Problem:** All existing `PartExecutorImplTest` tests used `innerFeedbackLoop = null`, so the full end-to-end path through the inner loop had zero coverage at the `PartExecutorImpl` level.

**Fix:** Added 2 new tests in `PartExecutorImplTest` under `GIVEN a doer+reviewer executor with InnerFeedbackLoop wired`:
1. **Result is PartResult.Completed**: Wires a real `InnerFeedbackLoop` with fake deps, creates a `critical__test-issue.md` in `pending/`, exercises the full path: doer COMPLETED -> reviewer NEEDS_ITERATION -> inner loop processes feedback (ADDRESSED) -> reviewer re-instructed -> reviewer PASS -> Completed.
2. **Feedback file moves from pending to addressed**: Same setup with `important__check.md`, verifies the file physically moves from `pending/` to `addressed/` during the inner loop.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt` | Added `hasRecognizedSeverityPrefix`, `findUnrecognizedPrefixFiles` companion methods; added pre-loop validation in `execute()` |
| `app/src/test/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoopTest.kt` | Added 2 tests for unrecognized prefix validation |
| `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` | Added 2 tests with InnerFeedbackLoop wired into PartExecutorDeps |

## Test Results

All tests pass (BUILD SUCCESSFUL). New test count:
- InnerFeedbackLoopTest: 18 tests (was 16)
- PartExecutorImplTest: 28 tests (was 26)
