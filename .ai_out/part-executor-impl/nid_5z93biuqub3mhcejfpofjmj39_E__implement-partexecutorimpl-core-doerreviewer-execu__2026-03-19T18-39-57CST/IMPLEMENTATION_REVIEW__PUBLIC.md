# NEEDS_ITERATION

## Summary

PartExecutorImpl implements the core doer/reviewer execution loop. The implementation is solid overall -- clean separation of concerns, good use of FakeAgentFacade for testing, proper structured logging, and correct R6 constraint adherence (no Clock, SessionsState, AgentUnresponsiveUseCase, ContextWindowStateReader in constructor). The code handles most spec requirements correctly.

However, there are two important issues and several suggestions that should be addressed.

---

## What's Good

1. **R6 compliance**: Constructor correctly excludes Clock, SessionsState, AgentUnresponsiveUseCase, ContextWindowStateReader -- all delegated to AgentFacade.
2. **PUBLIC.md validation**: Done after every `AgentSignal.Done` (COMPLETED, PASS, NEEDS_ITERATION), before proceeding. Correct per spec.
3. **Git commit after every Done**: `afterDone` called consistently after every validated Done signal.
4. **Session cleanup**: Sessions killed in all terminal paths (success, failure, crash).
5. **Illegal signal detection**: Done(PASS) and Done(NEEDS_ITERATION) in doer-only path throw `IllegalStateException`. Reviewer Done(COMPLETED) also throws.
6. **Context window state read at done boundaries**: Correctly called via `afterDone`.
7. **SelfCompacted handling**: Correctly errors out since it should never reach the executor.
8. **Test coverage**: Comprehensive -- covers happy paths, error paths, iteration, budget exceeded, PUBLIC.md validation, session kill verification, context window reads.
9. **PublicMdValidator**: Clean SRP separation with sealed ValidationResult.
10. **SubPartConfig**: Immutable static config, mutable iteration state stays in executor. Good separation.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### I1. Reviewer spawned eagerly -- deviates from spec flow

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, line 111-112

The spec (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E, "Flow" section) says:
- Step 1: Spawn doer, send instructions, await signal
- Step 2: On doer Done(COMPLETED) -> "First iteration: **spawn** reviewer TMUX session"
- Step 2b: "Subsequent iterations: reviewer session **already alive**"

The implementation spawns BOTH doer and reviewer at the start of `executeDoerWithReviewer`:

```kotlin
private suspend fun executeDoerWithReviewer(revConfig: SubPartConfig): PartResult {
    val doerHandle = spawnSubPart(doerConfig, isDoer = true)
    val reviewerHandle = spawnSubPart(revConfig, isDoer = false)  // <-- eager, should be lazy
    var doerSignal = sendDoerInstructions(doerHandle, reviewerPublicMdPath = null)
    ...
}
```

**Why this matters**: The reviewer TMUX session is alive and consuming resources during the doer's entire first iteration. More importantly, if the doer fails (FailWorkflow, Crashed, or missing PUBLIC.md), a reviewer session was spawned unnecessarily. This is not catastrophic (sessions are killed on failure), but it deviates from the spec and wastes resources.

**Suggested fix**: Spawn the reviewer lazily after the doer's first Done(COMPLETED) is validated. Track `reviewerHandle` as nullable, spawn on first need:

```kotlin
private suspend fun executeDoerWithReviewer(revConfig: SubPartConfig): PartResult {
    val doerHandle = spawnSubPart(doerConfig, isDoer = true)
    var reviewerHandle: SpawnedAgentHandle? = null
    var doerSignal = sendDoerInstructions(doerHandle, reviewerPublicMdPath = null)

    while (true) {
        val doerResult = mapDoerSignalInReviewerPath(doerSignal, doerHandle, reviewerHandle)
        if (doerResult != null) return doerResult

        if (reviewerHandle == null) {
            reviewerHandle = spawnSubPart(revConfig, isDoer = false)
        }
        // ... rest of loop
    }
}
```

### I2. `transitionTo` return value discarded -- validation-only usage obscures intent

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, lines 90, 160, 190

The `transitionTo` extension function returns a `SubPartStateTransition`, but the return value is discarded every time it's called. The function is only used for its validation side-effect (throws on invalid transition). While this is technically correct, it hides intent -- a reader might wonder if the return value should have been used.

```kotlin
// Line 90 -- return value discarded:
doerStatus.transitionTo(signal)
doerStatus = SubPartStatus.COMPLETED
```

**Suggested fix**: Either (a) add an explicit comment on the first occurrence explaining that `transitionTo` is called for validation, or (b) consider a `validateTransitionFor(signal)` wrapper that returns Unit to make intent explicit.

### I3. Test duplication -- many tests re-create identical setup

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt`

The test has significant setup duplication across doer+reviewer scenarios. For example, the iteration test (lines 464-539) and the context window test (lines 742-772) both set up identical signal queues (COMPLETED, NEEDS_ITERATION, COMPLETED, PASS) with identical facade wiring. The BDD GIVEN/WHEN blocks could be shared to DRY this up.

Multiple tests also repeat the same pattern of creating doer/reviewer configs, handles, signal queues, and facade wiring. Consider extracting a shared GIVEN block for the "two-iteration happy path" setup that multiple `it` blocks can share.

This is less critical in tests (per CLAUDE.md: "Most important in business rules. Much less important in tests and boilerplate"), but the duplication is substantial enough to warrant extraction.

### I4. Logging uses `ValType.STRING_USER_AGNOSTIC` generically instead of semantically specific types

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, lines 198-199, 215, 267

Per CLAUDE.md: "`ValType` must be **semantically specific** to the value being logged."

The implementation uses `ValType.STRING_USER_AGNOSTIC` for multiple semantically different values:
- Max iterations count (line 199)
- Current iteration count (line 200)
- Sub-part name (line 215)
- Context window remaining percentage (line 267)

These should use dedicated `ValType` entries (e.g., `ITERATION_COUNT`, `SUB_PART_NAME`, `CONTEXT_WINDOW_REMAINING`) or at minimum more specific existing types if available.

---

## Suggestions

### S1. `PartExecutorDeps` data class -- consider whether it hides dependency count smell

`PartExecutorDeps` groups 6 dependencies into a bundle to satisfy detekt's parameter-count threshold. While this is pragmatic, it masks a potential SRP signal -- if the class needs 6+ collaborators, it might be doing too much. Currently acceptable given the orchestration nature of the class, but worth flagging for awareness.

### S2. `FailedToConverge` error message embeds value in string

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`, line 206

```kotlin
return PartResult.FailedToConverge("Iteration budget exhausted after $currentIteration iterations")
```

Per CLAUDE.md logging standards, values should not be embedded in message strings. However, this is a `PartResult` summary string, not a log message, so this is borderline. Structured data could be exposed via a richer `FailedToConverge` data class with separate fields.

### S3. Test helper `createPublicMdFile` creates temp directories that are never cleaned up

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt`, line 78-83

The `createPublicMdFile` helper creates temporary directories via `Files.createTempDirectory` but there's no cleanup (no `afterEach` or `afterSpec` to delete them). While not a correctness issue, it leaves temp files around after test runs. Consider using Kotest's `tempdir()` or adding cleanup.

### S4. `PublicMdValidator` could be a companion object / static utility

`PublicMdValidator` is a stateless class with a single method. It's instantiated as a default in `PartExecutorDeps`. Since it has no dependencies and no state, it could be a companion object method or a static utility. However, the current design does allow for testing with a mock validator if needed, so this is a minor style point.

---

## Test Coverage Assessment

The test coverage is strong. All major paths are covered:
- Doer-only: COMPLETED, FailWorkflow, Crashed, PASS (illegal), NEEDS_ITERATION (illegal), missing PUBLIC.md, empty PUBLIC.md, context window read, session kill, spawn config, git commit
- Doer+reviewer: happy path, both sessions killed, iteration loop, git commits per done, budget exceeded, budget extended, doer FailWorkflow, reviewer Crashed, missing reviewer PUBLIC.md, missing doer PUBLIC.md, context window reads, sendPayload call count

**Not covered but acceptable**: The reviewer sending Done(COMPLETED) path (line 168-170) is not tested. It would throw `IllegalStateException`. Consider adding a test for completeness, similar to doer PASS/NEEDS_ITERATION tests.

---

## Documentation Updates Needed

None identified -- the code aligns with existing documentation and anchor points are properly referenced.
