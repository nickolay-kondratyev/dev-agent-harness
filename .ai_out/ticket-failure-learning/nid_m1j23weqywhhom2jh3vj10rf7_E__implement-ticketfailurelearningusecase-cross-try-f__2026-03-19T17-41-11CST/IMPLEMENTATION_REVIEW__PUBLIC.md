# Implementation Review -- TicketFailureLearningUseCase

## Summary

The implementation adds `TicketFailureLearningUseCaseImpl` which:
1. Maps `PartResult` sealed variants to structured failure context
2. Runs a non-interactive ClaudeCode agent (sonnet, 20min) to analyze `.ai_out/` artifacts
3. Builds a `### TRY-{N}` markdown section (structured facts + agent summary or fallback)
4. Appends to ticket file, commits on try branch, best-effort propagates to originating branch
5. Never throws -- all errors logged as WARN

Files changed: interface file (added data classes), new impl, new test. The caller (`FailedToExecutePlanUseCase`) and its tests were NOT modified. No existing tests removed.

**Tests pass. Sanity check passes.**

Overall: solid implementation that follows the spec closely. A few issues worth addressing.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Propagation leaves working tree on wrong branch if failure occurs between checkout steps

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-8/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCaseImpl.kt`, lines 194-218

The `propagateToOriginatingBranch()` method runs 4 sequential git commands. If, for example, the `git commit` on the originating branch fails (step 3), the catch block calls `tryCheckoutBranch(runContext.branchName)`, which is good. However, the originating branch now has uncommitted staged changes (the ticket file was checked out from the try branch at step 2). This leaves the originating branch dirty.

While the `tryCheckoutBranch` back to the try branch will likely succeed (git allows switching with staged changes that don't conflict), the originating branch is left with a staged-but-uncommitted file that could surprise future operations.

**Suggested fix**: Before `tryCheckoutBranch` in the catch block, do a best-effort `git checkout -- {ticketPath}` or `git reset HEAD -- {ticketPath}` on the originating branch to undo the staged file. Or more simply, use `git stash` before switching. This is admittedly a narrow edge case given the "best-effort" contract, so it is acceptable to defer to a follow-up ticket if you prefer.

### 2. `FailureLearningRunContext` mixes two concerns: run identity and failure-specific context

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-8/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCase.kt`, lines 28-58

`FailureLearningRunContext` contains both run-level identity fields (ticketPath, tryNumber, branchName, originatingBranch, workingDirectory) AND failure-specific fields (workflowType, failedAt, iteration, partsCompleted). The spec's `FailureLearningRequest` separates these into `FailureLearningRequest` + `PartResultFailureContext`.

Currently, the failure-specific fields (failedAt, iteration, partsCompleted, workflowType) are injected as constructor dependencies but they are per-failure values. This means a new `TicketFailureLearningUseCaseImpl` instance must be constructed for each failure, which is fine for now but violates the typical pattern where use cases are long-lived singletons.

This is non-blocking because the caller currently creates the use case per run anyway (via `TicketShepherdCreator`), but it should be called out for awareness. If the wiring ever changes to singleton scope, this will silently produce wrong results.

**Suggested fix**: Consider passing `workflowType`, `failedAt`, `iteration`, and `partsCompleted` as method parameters or as part of a separate request object passed to `recordFailureLearning()`, keeping `FailureLearningRunContext` as truly run-level context. This would also eliminate the need for `mapToFailureContext` to exist at all since the caller would provide the failure context directly. However, this changes the interface signature, which the exploration doc explicitly chose to avoid. Acceptable to defer.

### 3. Temp directories not cleaned up in tests

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-8/app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCaseImplTest.kt`

Every `it` block calls `createFixture(outFactory)` which creates a new `Files.createTempDirectory(...)`. These temp directories are never cleaned up (no `afterEach` or `afterSpec` block). Over many test runs, this accumulates orphan directories in `/tmp/`.

**Suggested fix**: Add cleanup in an `afterEach` block, or use a shared temp directory per describe block with cleanup.

---

## Suggestions

### 1. `mapToFailureContext` only extracts the `failureType` from `PartResult` -- the rest comes from `runContext`

The function name `mapToFailureContext` implies it transforms `PartResult` into `PartResultFailureContext`, but only one field (`failureType`) actually comes from the `partResult` argument. The other 4 fields are copied from `runContext`. This is not wrong, but the naming slightly overstates what the function does. A name like `buildFailureContext` would be more accurate since it combines data from two sources.

### 2. Consider logging the agent output on failure for debugging

In `runAnalysisAgent()`, when the agent returns `Failed`, the `result.output` (which may contain useful error information) is not logged -- only the exit code is. Adding the output as a second `Val` would help debugging without much cost.

### 3. The `it("THEN includes agent-generated summary")` test at line 205 has 3 assertions

Per CLAUDE.md testing standards: "Each `it` block contains one logical assertion." The test at line 205-212 checks three `shouldContain` assertions. While these are logically related (they verify the agent summary appeared), splitting them into separate `it` blocks would be more consistent with the project convention. This is a minor point.

### 4. `PartResultFailureContext` is declared public but documented as "internal to the impl"

The KDoc on `PartResultFailureContext` says "Internal to the impl" but the class is public (needed for tests that call `internal` methods). Consider whether `internal` visibility at the Kotlin level would be appropriate, since the tests are in the same module and can access `internal` classes.

---

## Documentation Updates Needed

None required. The spec already documents the design. The CLAUDE.md spec reference table already has the AP for this use case.

---

## Overall Assessment: APPROVE_WITH_SUGGESTIONS

The implementation is correct, follows the spec faithfully, maintains the non-fatal contract, has good test coverage of the key scenarios, and does not break any existing functionality. The issues identified are non-blocking and can be addressed in follow-up work. The code is clean, well-structured, and follows project patterns (constructor injection, structured logging, BDD tests, fakes over mocks).
