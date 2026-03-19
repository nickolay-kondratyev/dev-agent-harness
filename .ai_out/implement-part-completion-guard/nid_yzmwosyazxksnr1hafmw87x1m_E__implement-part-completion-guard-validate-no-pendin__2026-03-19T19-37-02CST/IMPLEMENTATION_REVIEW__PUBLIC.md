# Implementation Review: Part Completion Guard (Gate 5, R8)

## Verdict: PASS

The implementation is clean, well-tested, correctly aligned with the spec, and follows project conventions. All tests pass (both `sanity_check.sh` and `test.sh`). No critical or important issues found.

---

## Summary

**What changed:**
- New class `PartCompletionGuard` (75 lines) implements R8 from the granular-feedback-loop spec.
- Integrated into `PartExecutorImpl.mapReviewerSignal()` PASS branch, chained after PUBLIC.md validation using the existing null-coalescing pattern.
- Added to `PartExecutorDeps` with a default instance (constructor injection, consistent with `PublicMdValidator`).
- 13 unit tests for the guard in isolation, 5 integration-level tests in `PartExecutorImplTest`.
- No existing tests removed or modified in behavior.

**Spec alignment (R8):**
- After reviewer PASS + PUBLIC.md validation: checks `pending/` for `critical__*` and `important__*` -- correct.
- If found: returns `PartResult.AgentCrashed` -- correct (no retry, as spec requires).
- Remaining `optional__*` moved to `addressed/` -- correct.
- Empty or non-existent pending dir passes -- correct.

---

## What's Done Well

1. **Clean SRP separation.** `PartCompletionGuard` is a focused class with a single responsibility. It does not know about `PartResult`, `AgentSignal`, or any executor internals. The mapping from `GuardResult` to `PartResult` lives in `PartExecutorImpl.validateCompletionGuardOrCrash()`.

2. **Correct guard ordering.** The guard runs after PUBLIC.md validation but before state transitions and `afterDone()`. This means on guard failure, no git commit happens and no state transition occurs -- only `reviewerStatus = FAILED` and session cleanup.

3. **Consistent integration pattern.** The `validateCompletionGuardOrCrash()` method follows the exact same pattern as `validatePublicMdOrCrash()` -- return `PartResult.AgentCrashed?` with null meaning "passed". The null-coalescing chain (`?: validateCompletionGuardOrCrash(...) ?: run { ... }`) reads naturally.

4. **Good test coverage.** All spec-required scenarios are covered:
   - Empty pending -> Passed
   - Non-existent pending -> Passed
   - Critical in pending -> Failed
   - Important in pending -> Failed
   - Only optional -> Passed + files moved
   - Mixed optional + critical -> Failed (optional NOT moved)
   - Mixed optional + important -> Failed
   - Multiple optional -> all moved
   - Multiple blocking -> all listed in message
   - Content preservation after move

5. **Proper use of `ProtocolVocabulary.SeverityPrefix`** constants instead of hardcoded strings. Single source of truth for prefix values.

6. **Sealed class `GuardResult`** with `Passed` and `Failed` -- no `else` branch needed, compiler-enforced exhaustiveness.

7. **No existing tests removed.** The diff is purely additive to `PartExecutorImplTest`.

---

## No Critical Issues

---

## No Important Issues

---

## Suggestions (Optional)

### S1: `Pair` usage in test helper

`PartCompletionGuardTest.createTempFeedbackDirs()` returns `Pair<Path, Path>`. Per CLAUDE.md, `Pair`/`Triple` should be replaced with descriptive data classes. This is a minor inconsistency -- the destructuring `val (pending, addressed) = createTempFeedbackDirs()` is clear in context, and this is test-only code. A small `data class FeedbackDirs(val pending: Path, val addressed: Path)` would be marginally cleaner but is not important.

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartCompletionGuardTest.kt`, lines 13-20.

### S2: Files with unrecognized prefixes are silently ignored

If a file in `pending/` has a prefix that is neither `critical__`, `important__`, nor `optional__` (e.g., `unknown__something.md`), the guard will pass and the file will remain in `pending/` without being moved. The spec says only three valid prefixes exist, so this is a theoretical edge case (a broken reviewer would be caught by other guards). However, a defensive approach would be to treat unrecognized files as blocking (or log a warning). This is low priority since the reviewer is the only writer of these files and the spec defines only three valid prefixes.

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartCompletionGuard.kt`, lines 44-55.

### S3: Test setup duplication in `PartExecutorImplTest` guard tests

The five new guard tests in `PartExecutorImplTest` (lines 893-1071) share significant boilerplate for setting up `feedbackDir`, `signalQueue`, `facade`, and `spawnQueue`. This is consistent with how the existing tests in this file are structured (each test is self-contained), so it follows the established pattern. A shared helper like `buildGuardTestScaffolding(pendingFiles: List<String>)` could reduce duplication, but this is a minor style preference.

---

## Documentation Updates Needed

None. The `CLAUDE.md` spec references and anchor points are correctly maintained. The implementation summary in `1_IMPLEMENTATION_WITH_SELF_PLAN__PUBLIC.md` accurately reflects what was built.
