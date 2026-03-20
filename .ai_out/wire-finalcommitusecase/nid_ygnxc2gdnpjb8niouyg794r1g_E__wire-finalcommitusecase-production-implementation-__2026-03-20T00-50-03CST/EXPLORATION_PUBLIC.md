# Exploration: FinalCommitUseCase Production Wiring

## Current State
- `FinalCommitUseCase` is a `fun interface` at `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCase.kt`
- Single method: `suspend fun commitIfDirty()`
- `TicketShepherdCreatorImpl` (line 138-140) has a TODO stub lambda

## Key Pattern: CommitPerSubPart
The closest existing pattern is `CommitPerSubPart` in `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt`:
1. `git add -A` via `GitCommandBuilder` + `ProcessRunner`
2. `git diff --cached --quiet` — exit 0 = no changes (skip), non-zero = changes exist
3. `git commit --author=... -m ...`
4. All failures delegated to `GitOperationFailureUseCase`

## Dependencies Available
- `ProcessRunner` — from asgardCore, runs CLI commands
- `GitCommandBuilder` — builds git command arrays with optional `-C` workingDir
- `GitOperationFailureUseCase` — handles git failures (index.lock recovery, escalation)
- `OutFactory` — structured logging

## Wiring Note
- `TicketShepherdCreatorImpl` does NOT currently have `ProcessRunner` as a constructor param
- `GitBranchManager` wraps `ProcessRunner` but doesn't expose it
- The `FinalCommitUseCase` is a constructor param with default lambda — can be replaced with a factory or direct impl
- `GitOperationFailureUseCase` is created inside `wireTicketShepherd()` as part of `FailedToExecutePlanUseCaseImpl` — but `FinalCommitUseCase` needs it too

## Commit Message Convention
- Per `doc/core/git.md`: `[shepherd] {part_name}/{sub_part_name} — {result}`
- For final commit, a simpler message like `[shepherd] final-state-commit` is appropriate

## Test Patterns
- `CommitPerSubPartTest` exists — uses fake ProcessRunner
- BDD DescribeSpec with GIVEN/WHEN/THEN structure
- Tests: commits when dirty, skips when clean, handles failures
