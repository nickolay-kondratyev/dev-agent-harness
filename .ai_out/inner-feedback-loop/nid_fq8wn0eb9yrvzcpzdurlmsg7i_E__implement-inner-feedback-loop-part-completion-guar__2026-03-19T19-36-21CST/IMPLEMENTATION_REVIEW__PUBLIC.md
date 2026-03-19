# Implementation Review: Inner Feedback Loop

## Summary

The implementation adds `InnerFeedbackLoop` as a new class with clear SRP separation from `PartExecutorImpl`. The outer loop in `PartExecutorImpl` was restructured: the old pattern of re-instructing the doer directly after `NEEDS_ITERATION` is replaced with a delegation to `InnerFeedbackLoop` (when wired) which processes feedback files one-at-a-time in severity order. The change is backward compatible via `innerFeedbackLoop: InnerFeedbackLoop? = null`.

Tests pass (`:app:test` BUILD SUCCESSFUL, `sanity_check.sh` passes). 16 new tests in `InnerFeedbackLoopTest`, 5 existing tests updated in `PartExecutorImplTest`.

**Overall assessment: Solid implementation with good SRP extraction. Two important issues and a few suggestions below.**

---

## CRITICAL Issues

None found.

---

## IMPORTANT Issues

### I1: `sortBySeverity` silently drops files with unrecognized severity prefixes

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt`, lines 364-381.

`sortBySeverity` filters files into three buckets (`critical__*`, `important__*`, `optional__*`) and concatenates. Any file in `pending/` that does NOT match one of the three prefixes is silently dropped from processing. This is a real scenario -- a reviewer could write a file with a typo like `critcal__foo.md` or an unrecognized prefix.

The file would sit in `pending/` unprocessed, and then the post-loop bug guard (line 134) only checks for `critical__*` or `important__*` -- so a typo'd `critcal__foo.md` would also escape the guard. The feedback item would be permanently orphaned.

**Suggested fix:** Either (a) add a pre-loop validation that all files in `pending/` have a recognized severity prefix and return `AgentCrashed` if any don't, or (b) treat unrecognized prefixes as an error immediately. This aligns with the spec's principle of "missing marker -> AgentCrashed" -- same rigor for malformed filenames.

### I2: `buildFeedbackItemRequest` does not pass the actual feedback file content/path to the doer

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt`, lines 414-428.

The spec (R3, R4) requires the doer to receive "focused instruction per item" including the feedback file content and the feedback file path. However, `buildFeedbackItemRequest` creates a generic `DoerRequest` with `reviewerPublicMdPath = null` and no reference to the specific feedback file being processed. The `ContextForAgentProvider` has no way to know WHICH feedback file to include in the instructions.

The implementation summary acknowledges this: "The actual per-item instruction content (FeedbackItem section) will be assembled by ContextForAgentProvider based on the request type." But since `DoerRequest` carries no feedback-file-specific information, the context provider cannot assemble per-item instructions. The doer would receive a generic re-instruction with no feedback content.

This means the inner loop structurally works (files get processed, moved, committed) but the doer receives no actual feedback content to act on. This is likely deferred to the `ContextForAgentProvider` update (Gate 2/R4 from the spec), but the current `InnerFeedbackLoop` tests pass because `fakeContextProvider()` returns a dummy instruction file -- the tests don't verify instruction content.

**This should be called out explicitly as a known gap** -- either as a follow-up ticket or a TODO with anchor point. The inner loop is wired and tested for orchestration, but the doer instruction assembly for per-item feedback is not yet functional.

### I3: Existing PartExecutorImpl tests lost the doer re-instruction step without replacement coverage

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt`

The old iteration flow was: doer COMPLETED -> reviewer NEEDS_ITERATION -> doer re-instructed (4th signal) -> reviewer PASS. The new flow removes the doer re-instruction from `PartExecutorImpl` (now delegated to `InnerFeedbackLoop`). The tests correctly updated signal counts from 4 to 3.

However, there is no integration-level test that exercises `PartExecutorImpl` WITH `innerFeedbackLoop` wired in. All existing `PartExecutorImplTest` tests use `innerFeedbackLoop = null`. This means the full end-to-end path (outer loop delegates to inner loop, inner loop processes items, control returns to outer loop for reviewer re-instruction) has zero test coverage at the `PartExecutorImpl` level.

**Suggested fix:** Add at least one test in `PartExecutorImplTest` that wires a real or fake `InnerFeedbackLoop` into `PartExecutorDeps` and exercises the NEEDS_ITERATION -> inner loop -> reviewer re-instruction path.

---

## Suggestions

### S1: `moveFile` is a static method doing real I/O -- consider making it injectable for testability

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt`, lines 397-404.

`moveFile` is in `companion object` and does real filesystem I/O (`Files.move`). The tests exercise it against real temp directories which works fine, but if you ever need to test move failures or atomic move semantics, there's no seam. Low priority -- the current test approach with temp dirs is pragmatic.

### S2: Consider using `data object` instead of `object` for `InnerLoopOutcome.Continue`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt`, line 35.

`object Continue` could be `data object Continue` for consistent `toString()` output. Same applies to other singleton sealed class members. Minor Kotlin style point.

### S3: `feedbackDir` on `SubPartConfig` is non-null asserted at runtime

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, line 256.

`revConfig.feedbackDir ?: error("Reviewer config must have feedbackDir")` and the identical check at line 309 -- both are runtime checks for what should be a compile-time guarantee. The same pattern appears in `sendReviewerInstructions`. If `SubPartConfig` were split into `DoerSubPartConfig` and `ReviewerSubPartConfig` (where `feedbackDir` is non-optional on the reviewer), this class of error would be impossible. This is a pre-existing design concern, not introduced by this PR, so flagging as suggestion only.

---

## Documentation Updates Needed

None specifically required by this change. The implementation summary (`1_IMPLEMENTATION_WITH_SELF_PLAN__PUBLIC.md`) accurately describes what was done.

---

## Verdict

**Approve with required changes (I1, I2, I3).**

- I1 (unrecognized severity prefix silently dropped) is a real correctness gap that should be addressed before merging.
- I2 (doer receives no feedback content) should be explicitly tracked as a follow-up ticket if not addressed in this PR.
- I3 (no integration test of PartExecutorImpl with inner loop wired) should have at least one test added.
