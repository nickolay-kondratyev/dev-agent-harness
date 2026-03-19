# Implementation Summary — TicketFailureLearningUseCase

## What Was Done

Implemented the `TicketFailureLearningUseCaseImpl` which records structured failure context to the ticket file after a failed shepherd run, enabling cross-try learning. The implementation follows the spec at ref.ap.cI3odkAZACqDst82HtxKa.E.

### Key behaviors:
1. Maps `PartResult` sealed class variants to structured failure context (`PartResultFailureContext`)
2. Runs a non-interactive ClaudeCode agent (sonnet, 20min timeout) to analyze `.ai_out/` artifacts and produce a summary
3. Falls back to a static message when the agent fails or times out
4. Appends a `### TRY-{N}` section to the ticket file under `## Previous Failed Attempts`
5. Commits the ticket update on the try branch
6. Best-effort propagates to the originating branch via git checkout + cherry-pick file
7. **Never throws** — entire method wrapped in try/catch, all errors logged as WARN

## Files Created

- `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCaseImpl.kt` — Full implementation
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCaseImplTest.kt` — Comprehensive unit tests

## Files Modified

- `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCase.kt` — Added `FailureLearningRunContext` and `PartResultFailureContext` data classes, updated KDoc

## Test Results

All tests pass (`./gradlew :app:test` exits with code 0). Test cases cover:
- Happy path: agent succeeds, TRY-N section appended correctly with all structured facts
- Agent failure: fallback summary used, ticket still updated
- Agent timeout: same fallback behavior, partial output not leaked
- Ticket already has `## Previous Failed Attempts`: new TRY-N appended without duplicating heading
- Git commit failure: WARN logged, no exception thrown, ticket still updated
- Propagation failure: WARN logged, learning preserved on try branch
- All PartResult variants map correctly to failure context
- Agent instructions contain required facts, artifact directory, and expected output format
- Empty partsCompleted shows "none"

## Design Decisions

1. **Interface signature preserved**: `suspend fun recordFailureLearning(partResult: PartResult)` — no changes to the caller (`FailedToExecutePlanUseCase`) or its tests.

2. **Run context as constructor dependency**: `FailureLearningRunContext` carries workflow-level info (workflowType, failedAt, iteration, partsCompleted) since `PartResult` only contains the failure type + message string. This is set once at construction time when the shepherd run context is known.

3. **PartResult → failureType mapping**: Simple sealed class variant name extraction. `Completed` variant maps to "Completed" (defensive — shouldn't happen in practice since the caller filters for failure results).

4. **Git operations via ProcessRunner**: Uses `runProcess` with `git -C <workDir>` pattern, consistent with `GitBranchManagerImpl`.

5. **Propagation checkout-file approach**: Uses `git checkout {tryBranch} -- {ticketPath}` to cherry-pick just the ticket file rather than full cherry-pick, avoiding merge conflicts.

## Notes

- The `NoOpTicketFailureLearningUseCase` is preserved for use when failure learning is not needed.
- The `SpyTicketFailureLearningUseCase` in `FailedToExecutePlanUseCaseImplTest.kt` continues to compile unchanged since the interface signature was not modified.
- The implementation does NOT wire into `TicketShepherdCreator` — that wiring is a separate concern for when the caller is ready to use the real implementation instead of the no-op stub.
