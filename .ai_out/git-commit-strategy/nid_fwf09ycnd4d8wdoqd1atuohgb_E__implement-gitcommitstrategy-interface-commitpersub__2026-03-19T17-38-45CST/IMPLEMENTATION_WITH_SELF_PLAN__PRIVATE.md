# Implementation: GitCommitStrategy + CommitPerSubPart

## Status: COMPLETE

## Plan
1. [x] Create `SubPartDoneContext` data class
2. [x] Create `GitCommitStrategy` interface with companion factory
3. [x] Create `CommitPerSubPart` implementation
4. [x] Create unit tests with BDD style
5. [x] Fix detekt SpreadOperator warnings
6. [x] All tests pass

## Design Decisions
- `gitUserEmail` injected via constructor (read once at wiring time) per spec: "Commit email stays as-is"
- `SubPartDoneContext.agentType` uses `AgentType` enum directly (not String) to avoid mapping at commit time
- `hasStagedChanges()` catches all exceptions from `git diff --cached --quiet` since ProcessRunner throws on non-zero exit
- Followed GitBranchManager pattern: interface + impl in same file, companion factory, gitCommand() helper

## Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/CommitPerSubPartTest.kt`
