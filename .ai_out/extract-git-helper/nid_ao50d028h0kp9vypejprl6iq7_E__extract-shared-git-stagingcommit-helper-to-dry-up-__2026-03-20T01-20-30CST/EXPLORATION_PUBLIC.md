# Exploration: Extract Shared Git Staging/Commit Helper

## Duplication Identified

### File 1: `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt`
- `stageAll()` (lines 45-56): `git add -A` with error handling via `gitOperationFailureUseCase`
- `hasStagedChanges()` (lines 66-75): `git diff --cached --quiet` — exception = changes exist
- `commit()` (lines 82-93): `git commit -m <message>` with error handling

### File 2: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt`
- `CommitPerSubPart.stageAll()` (lines 132-144): identical pattern, but derives `GitFailureContext` from `SubPartDoneContext`
- `CommitPerSubPart.hasStagedChanges()` (lines 154-163): **identical** to FinalCommitUseCaseImpl
- `CommitPerSubPart.commit()` (lines 170-190): same pattern, but adds `--author` arg

## Shared Pattern
All three methods follow: build command → run via ProcessRunner → on failure delegate to GitOperationFailureUseCase with GitFailureContext.

## Dependencies
- `ProcessRunner` (from asgard) — runs git commands
- `GitCommandBuilder` — builds `["git", ...]` or `["git", "-C", "<dir>", ...]`
- `GitOperationFailureUseCase` — handles failures (index.lock recovery, fail-fast)
- `GitFailureContext` — data class for error reporting context

## Test Files
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImplTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/CommitPerSubPartTest.kt`
- Both use `RecordingFakeProcessRunner` and `FakeGitOperationFailureUseCase` (defined in CommitPerSubPartTest.kt)

## Extraction Target
A `GitStagingCommitHelper` in `core/supporting/git/` that encapsulates:
1. `stageAll(failureContext)` — `git add -A` + error handling
2. `hasStagedChanges()` — `git diff --cached --quiet` check
3. `commit(args, failureContext)` — run commit command + error handling (caller builds the commit args)
