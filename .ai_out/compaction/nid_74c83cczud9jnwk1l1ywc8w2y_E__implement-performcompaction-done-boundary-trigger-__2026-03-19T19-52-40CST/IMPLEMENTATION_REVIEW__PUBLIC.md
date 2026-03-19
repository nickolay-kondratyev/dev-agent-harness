# Implementation Review: performCompaction() + Done-Boundary Trigger Detection

**Verdict: CONDITIONAL PASS** -- One important bug must be fixed. Other items are suggestions.

---

## Summary

The implementation adds self-compaction at done boundaries to `PartExecutorImpl`. The overall architecture is clean: `CompactionOutcome` sealed class cleanly models the three outcomes, `PrivateMdValidator` is properly extracted following the existing `PublicMdValidator` pattern, and the `afterDone()` method elegantly encapsulates the git-commit + threshold-check + compaction flow. Tests cover all 8 required scenarios plus 2 additional boundary tests (threshold=35 and threshold=36). All 42 tests pass.

---

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. Reviewer PASS path ignores CompactionOutcome -- silent failure on compaction

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, lines 238-243

```kotlin
DoneResult.PASS -> {
    // ...
    afterDone(revConfig, signal.result, reviewerHandle)  // <-- return value DISCARDED
    killAllSessions(doerHandle, reviewerHandle)
    ReviewerSignalResult.Terminal(PartResult.Completed)
}
```

The `afterDone()` call returns `CompactionOutcome` but the return value is completely ignored. If compaction is triggered and fails (e.g., PRIVATE.md missing, Done-during-compaction protocol violation), the code still proceeds to return `PartResult.Completed`.

This is a **correctness bug**: a failed compaction during the reviewer PASS path would be silently swallowed, and the part would report success despite a broken agent.

**Fix:** Handle the `CompactionOutcome` the same way it is handled in `mapDoerSignalInReviewerPath` and `processNeedsIteration`:

```kotlin
DoneResult.PASS -> {
    val crash = validatePublicMdOrCrash(revConfig, doerHandle, reviewerHandle)
    if (crash != null) {
        ReviewerSignalResult.Terminal(crash)
    } else {
        reviewerStatus.transitionTo(signal)
        reviewerStatus = SubPartStatus.COMPLETED
        doerStatus = SubPartStatus.COMPLETED
        val compactionOutcome = afterDone(revConfig, signal.result, reviewerHandle)
        when (compactionOutcome) {
            is CompactionOutcome.CompactionFailed -> {
                doerHandle?.let { deps.agentFacade.killSession(it) }
                ReviewerSignalResult.Terminal(PartResult.AgentCrashed(compactionOutcome.reason))
            }
            else -> {
                killAllSessions(doerHandle, reviewerHandle)
                ReviewerSignalResult.Terminal(PartResult.Completed)
            }
        }
    }
}
```

**Add a test** for: reviewer PASS + low context + compaction failure (e.g., missing PRIVATE.md) should return AgentCrashed, not Completed.

### 2. Crashed signal during compaction does not kill session -- resource leak

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, lines 390-393

```kotlin
is AgentSignal.Crashed -> {
    // Agent crashed during compaction (e.g., timeout)
    CompactionOutcome.CompactionFailed(signal.details)
}
```

When the agent returns `Crashed` during compaction (e.g., timeout), the session is NOT killed via `killSession`. Compare with `Done` and `FailWorkflow` branches that DO call `killSession(handle)`.

The comment says "e.g., timeout" -- but even a timed-out agent may leave a TMUX session lingering. The caller (`mapDoerOnlyDone`) only kills sessions for `NoCompaction` and explicitly states "Session already killed during compaction" for `Compacted`. For `CompactionFailed`, the session cleanup depends entirely on `performCompaction` having done it.

**Fix:** Add `deps.agentFacade.killSession(handle)` to the `Crashed` branch in `performCompaction`, same as the `Done` and `FailWorkflow` branches.

---

## Suggestions

### 3. Temp file leak in performCompaction

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, lines 351-352

```kotlin
val instructionFile = Files.createTempFile("compaction-instruction-", ".md")
Files.writeString(instructionFile, instructionText)
```

The temp file is never deleted. While this is minor (OS will eventually clean `/tmp`), it is a resource leak pattern. Consider deleting it in a `finally` block or after `sendPayloadAndAwaitSignal` returns. Or use the project's `.tmp/` convention per CLAUDE.md.

### 4. `@Suppress("UnusedParameter")` on trigger parameter

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, line 333

The `trigger: CompactionTrigger` parameter in `performCompaction` is annotated with `@Suppress("UnusedParameter")`. This is fine for V1 (only DONE_BOUNDARY exists), but the suppress annotation should be removed when V2 adds `EMERGENCY_INTERRUPT` since the parameter will then need to be used for behavior branching. Consider adding a brief comment like `// V2: branch behavior by trigger type`.

### 5. Test for session rotation: verify PRIVATE.md path included in respawned instructions

The session rotation test (line 1149-1219) verifies spawn count and signal count but does not verify that the respawned doer's instructions include the PRIVATE.md content. This is the key value proposition of session rotation. Currently, this is implicitly tested via the `ContextForAgentProvider` fake returning a dummy path. A more targeted test could verify the `AgentInstructionRequest` passed to the context provider includes the `privateMdPath`.

---

## What Was Done Well

1. **Clean architecture**: `CompactionOutcome` sealed class is well-designed. The tri-state (NoCompaction/Compacted/CompactionFailed) cleanly models all outcomes and forces callers to handle each case (except for the PASS path bug noted above).

2. **PrivateMdValidator extraction**: Follows the existing `PublicMdValidator` pattern exactly -- consistent, testable, SRP-compliant.

3. **Thorough test coverage**: 10 new compaction tests cover all required scenarios plus boundary conditions. The test structure follows BDD conventions with clear GIVEN/WHEN/THEN naming. The FakeAgentFacade signal-queue pattern is clean and readable.

4. **Correct threshold boundary testing**: Tests for remaining=35 (triggers) and remaining=36 (does not trigger) verify the inclusive boundary behavior explicitly.

5. **Session rotation in doer+reviewer path**: The `DoerSignalResult.Continue(doerCompacted)` / `ReviewerSignalResult.Continue(reviewerCompacted)` pattern cleanly communicates compaction state back to the loop without breaking the existing control flow. `respawnAfterCompaction()` correctly skips NOT_STARTED validation.

6. **Spec compliance**: The implementation matches the spec (ref.ap.8nwz2AHf503xwq8fKuLcl.E) for all documented behaviors including: threshold check ordering (after PUBLIC.md validation), stale state handling (log warning, skip compaction), protocol violation handling (Done during compaction = immediate crash), and PRIVATE.md validation (missing/empty = crash).
