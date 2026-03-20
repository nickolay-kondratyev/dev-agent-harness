# Implementation: FinalCommitUseCase Production Wiring

## What Was Done
Implemented production `FinalCommitUseCaseImpl` and wired it into `TicketShepherdCreatorImpl`.

### FinalCommitUseCaseImpl
- Follows the exact same pattern as `CommitPerSubPart`: `git add -A` → `git diff --cached --quiet` → `git commit -m "[shepherd] final-state-commit"`
- No author attribution (no sub-part context available for final commit)
- Failures delegated to `GitOperationFailureUseCase` (index.lock recovery, fail-fast)
- `internal` visibility, instantiated via `FinalCommitUseCase.standard()` factory

### Wiring Changes
- `TicketShepherdCreatorImpl.finalCommitUseCase` param replaced with `finalCommitUseCaseFactory: FinalCommitUseCaseFactory`
- New `processRunnerFactory` param added (same pattern as `ContextInitializerImpl`)
- `GitOperationFailureUseCase` now wired inside `wireTicketShepherd()` with `StandardGitIndexLockFileOperations`
- New `FinalCommitUseCaseFactory` fun interface for testability

### Tests
- 13 unit tests covering: dirty commit, clean skip, git-add failure, git-commit failure
- 2 integration tests (gated by `isIntegTestEnabled()`) verifying real git operations
- All existing tests continue to pass
