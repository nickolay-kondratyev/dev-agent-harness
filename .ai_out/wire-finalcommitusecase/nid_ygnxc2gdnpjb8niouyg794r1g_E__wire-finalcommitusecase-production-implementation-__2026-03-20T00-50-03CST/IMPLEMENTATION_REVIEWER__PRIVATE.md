# Reviewer Private Notes: FinalCommitUseCaseImpl

## Review Process
1. Read all source files, reference pattern (`CommitPerSubPart`), and context docs
2. Ran `./test.sh` -- EXIT_CODE=0 (all tests pass)
3. Ran `./sanity_check.sh` -- EXIT_CODE=0
4. Verified `git diff main...HEAD` to confirm no existing tests removed
5. Reviewed `GitOperationFailureUseCase` contract to validate error handling flow

## Key Correctness Checks Performed

### Control flow after `stageAll` failure
- `GitOperationFailureUseCaseImpl.failFast()` returns `Nothing` -- so non-recoverable failures terminate the process
- Index.lock recovery path: `handleGitFailure` returns normally after successful retry, allowing execution to proceed to `hasStagedChanges()` -- this is correct because the `git add -A` was retried inside the handler

### `hasStagedChanges()` broad exception catch
- Same pattern as `CommitPerSubPart` -- catches all exceptions and treats them as "changes exist"
- A genuine git failure here would surface in the subsequent `commit()` call which properly delegates to `GitOperationFailureUseCase`
- Acceptable given the established pattern

### Integration test gating
- Uses both `@EnabledIf(IntegTestCondition::class)` at class level and `.config(enabled = isIntegTestEnabled())` on describe blocks
- Double gating is redundant but not harmful -- the `@EnabledIf` prevents the class from loading entirely when integ tests are disabled

### TicketShepherdCreatorTest changes
- Only change: `finalCommitUseCase = FinalCommitUseCase { ... }` -> `finalCommitUseCaseFactory = FinalCommitUseCaseFactory { _, _, _ -> ... }`
- All existing test cases preserved, no behavioral changes

## DRY Analysis Detail
The duplicated methods between `FinalCommitUseCaseImpl` and `CommitPerSubPart`:
- `stageAll`: identical except `CommitPerSubPart` takes `SubPartDoneContext` param for failure context
- `hasStagedChanges`: identical (copy-paste)
- `commit`: `CommitPerSubPart` version has author attribution; `FinalCommitUseCaseImpl` does not

A potential extraction would be a `GitStagingCommitter` helper that takes:
- processRunner, gitCommandBuilder, gitOperationFailureUseCase
- And exposes: `stageAll(context)`, `hasStagedChanges()`, `commit(message, author?, context)`

This would reduce both classes to thin orchestration wrappers. Filed as a follow-up suggestion.

## Things NOT flagged (intentionally)
- The `@Suppress("TooGenericExceptionCaught")` annotations -- necessary because ProcessRunner throws generic exceptions on non-zero exit
- The `@Suppress("SpreadOperator")` -- detekt rule, array spread is the only way to call vararg with dynamic arrays
- Test structure follows one-assert-per-test even though it means repeated setup -- consistent with CommitPerSubPartTest
