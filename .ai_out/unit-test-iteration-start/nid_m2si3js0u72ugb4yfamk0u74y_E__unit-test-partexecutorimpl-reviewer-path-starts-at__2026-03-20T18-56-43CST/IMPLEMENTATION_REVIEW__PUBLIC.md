# Implementation Review: Unit Test PartExecutorImpl Reviewer Path Starts at Iteration 1

## Summary

New unit tests were added to `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (lines 1739-1824) to verify the `currentIteration` starting value logic in `PartExecutorImpl`:

1. **Reviewer path starts at iteration 1** -- verified via `SubPartDoneContext.currentIteration` captured by `RecordingGitCommitStrategy`.
2. **Doer-only path stays at iteration 0** -- same verification mechanism.
3. **CommitMessageBuilder format** -- verified that the captured context produces `(iteration 1/4)` suffix when fed to `CommitMessageBuilder.build()`.

All tests pass. Sanity check passes. No pre-existing tests were removed or modified.

**Overall assessment: APPROVE** -- the tests are correct, focused, and follow existing patterns.

## No CRITICAL Issues

No security, correctness, or data-loss concerns found.

## No IMPORTANT Issues

No architecture violations or maintainability concerns.

## Suggestions

### 1. Implicit test ordering dependency (consistent with existing pattern, low priority)

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (lines 1780-1782, 1819-1821)

The second `it` blocks in both the reviewer-path and doer-only-path test groups rely on state populated by the first `it` block's `executor.execute()` call, without calling `execute()` themselves:

```kotlin
// Line 1775: first it block calls executor.execute()
it("THEN the first doer SubPartDoneContext has currentIteration = 1") {
    executor.execute()
    gitStrategy.calls.first().currentIteration shouldBe 1
}

// Line 1780: second it block relies on state from the first
it("THEN the first doer SubPartDoneContext has hasReviewer = true") {
    gitStrategy.calls.first().hasReviewer shouldBe true
}
```

This works because Kotest defaults to `SingleInstance` isolation mode where `it` blocks run sequentially and share state. This is **consistent with the existing pattern** used elsewhere in this test file (e.g., lines 818/842), so no change is required for consistency. However, if the codebase ever moves to `InstancePerTest` isolation, these tests would break. A more robust alternative would be to call `executor.execute()` in each `it` block with fresh fixtures, but that would deviate from the established convention in this file.

**Verdict:** Acceptable as-is given existing patterns. No action needed.

### 2. CommitMessageBuilder test exercises integration rather than unit behavior

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (lines 1784-1795)

The test at line 1784 reads the `SubPartDoneContext` captured from the executor and then passes it to `CommitMessageBuilder.build()`. This is effectively an integration test between `PartExecutorImpl` and `CommitMessageBuilder`. This is fine and valuable -- it verifies the end-to-end contract that the executor produces values that `CommitMessageBuilder` can consume without throwing `IllegalArgumentException` (which requires `currentIteration >= 1` when `hasReviewer` is true).

**Verdict:** Good test. It catches the exact bug class this ticket was about.

## Documentation Updates Needed

None.
