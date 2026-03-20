# Code Review: FinalCommitUseCaseImpl Production Wiring

## Summary

This change implements the production `FinalCommitUseCaseImpl`, wires it into `TicketShepherdCreatorImpl` via a `FinalCommitUseCaseFactory`, and adds comprehensive unit and integration tests. The implementation correctly follows the established `CommitPerSubPart` pattern: `git add -A` -> `git diff --cached --quiet` -> `git commit`. The TODO stub is removed. All tests pass (unit and sanity check).

**Overall assessment: GOOD. The implementation is correct, well-tested, and follows established patterns. One IMPORTANT issue around DRY and one suggestion below.**

## No CRITICAL Issues

No security, correctness, or data loss issues found.

- No existing tests were removed or weakened.
- No anchor points were removed.
- Error handling follows the established `GitOperationFailureUseCase` escalation pattern.
- The `hasStagedChanges()` broad exception catch is consistent with the `CommitPerSubPart` pattern and is safe because a genuine git failure would surface in the subsequent `commit()` call.

## IMPORTANT Issues

### 1. DRY Violation: `FinalCommitUseCaseImpl` duplicates `CommitPerSubPart` internals

`FinalCommitUseCaseImpl` contains three private methods (`stageAll`, `hasStagedChanges`, `commit`) that are near-identical copies of `CommitPerSubPart`'s methods. The only differences are:
- No author attribution in the commit command
- A static `FAILURE_CONTEXT` instead of one derived from `SubPartDoneContext`

**Files:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt` (lines 46-94)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt` (lines 133-190)

**Suggested fix:** Extract the shared git staging/diff/commit logic into a reusable helper (e.g., `GitCommitHelper` or similar) that both `CommitPerSubPart` and `FinalCommitUseCaseImpl` delegate to. This would centralize the `processRunner` interaction pattern and the `hasStagedChanges` "exception-means-changes-exist" convention in one place. This is a knowledge duplication (the git staging+diff+commit protocol) rather than mere code duplication.

**However:** This is a reasonable follow-up ticket rather than a blocker, since the pattern is small and the duplication is contained. The current code is correct.

## Suggestions

### 1. Unused import in `FinalCommitUseCaseImpl.kt`

`java.nio.file.Path` is imported on line 8 of `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt` but never used within the class. Remove it.

### 2. Test DRY: Repeated use-case construction in `FinalCommitUseCaseImplTest`

In `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImplTest.kt`, the four `it` blocks under "GIVEN working tree has uncommitted changes / WHEN commitIfDirty is called" each independently construct `RecordingFakeProcessRunner` + `FinalCommitUseCaseImpl` with identical setup. This could use a shared setup in the `describe("WHEN ...")` block. The same pattern repeats in the failure test groups. This is minor since the test file is still readable, but it adds up to significant vertical noise. The reference `CommitPerSubPartTest` has the same pattern, so this is consistent -- just calling it out as something that could be improved wholesale.

### 3. `processRunnerFactory` parameter typing

In `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` (line 158), `processRunnerFactory` is typed as a raw lambda `(OutFactory) -> ProcessRunner` rather than a `fun interface` like the other factories (`SetupPlanUseCaseFactory`, `AllSessionsKillerFactory`, `FinalCommitUseCaseFactory`). For consistency, consider wrapping it in a `fun interface ProcessRunnerFactory`. This is minor and non-blocking.

## Documentation Updates Needed

None required. The KDoc on `TicketShepherdCreatorImpl` was correctly updated to remove `finalCommitUseCase` from the "not yet wired" list and add `finalCommitUseCaseFactory` and `processRunnerFactory` param docs.

## Verdict

**Approve with the DRY concern noted as a follow-up.** The implementation is correct, tests are comprehensive and follow BDD style, wiring is clean, and the established patterns are followed faithfully. The unused import should be removed before merge.
