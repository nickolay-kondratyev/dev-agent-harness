# GitCommitStrategy + CommitPerSubPart Implementation

## Summary

Implemented the `GitCommitStrategy` interface and its V1 `CommitPerSubPart` implementation for harness-owned git commits. The strategy commits after every sub-part done signal, skipping when there are no staged changes.

## Files Created

### `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt`
Contains:
- **`SubPartDoneContext`** data class — hook context with part name, sub-part name, role, result, reviewer info, iteration config, agent type, and model.
- **`GitCommitStrategy`** interface — single `onSubPartDone(context)` hook with companion factory method `commitPerSubPart(...)`.
- **`CommitPerSubPart`** class — V1 implementation that:
  1. Stages all changes via `git add -A`
  2. Checks for staged changes via `git diff --cached --quiet` (exit 0 = no changes, skip commit)
  3. Builds commit message via `CommitMessageBuilder.build(...)`
  4. Builds author via `CommitAuthorBuilder.build(...)`
  5. Creates commit via `git commit --author="{author} <{email}>" -m "{message}"`
  6. Delegates git failures to `GitOperationFailureUseCase`

### `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/CommitPerSubPartTest.kt`
BDD unit tests covering:
- Happy path: changes exist, commit created with correct message and author
- Empty diff: no staged changes, commit skipped
- Git add failure: delegates to GitOperationFailureUseCase with correct context
- Git commit failure: delegates to GitOperationFailureUseCase
- Working dir: all git commands include `-C <workingDir>` when specified
- No reviewer: commit message omits iteration suffix
- PI agent type: uses PI short code in author

## Test Results
All tests pass (`./gradlew :app:test` exit code 0), including detekt static analysis.

## Design Decisions

1. **`gitUserEmail` as constructor parameter** — Per spec "Commit email stays as-is", the email is read once from git config at wiring time and injected, avoiding extra git calls per commit.

2. **`SubPartDoneContext.agentType` uses `AgentType` enum** — Not a String. The caller is responsible for mapping the string agent type from `SubPart` to the enum. This keeps the commit strategy type-safe and avoids parsing concerns.

3. **`hasStagedChanges()` catches all exceptions** — `ProcessRunner` throws on non-zero exit. `git diff --cached --quiet` returns non-zero when changes exist. Catching the exception is the idiomatic way to detect changes with this ProcessRunner contract.

4. **Followed `GitBranchManager` pattern** — Interface + implementation in same file, companion factory method, private `gitCommand()` helper for `-C <workingDir>` prefixing, `OutFactory` logging.

## Open Items
- Wiring into `TicketShepherdCreator` is NOT included in this ticket (separate concern).
- The caller must map `SubPart.agentType` (String like "ClaudeCode") to `AgentType` enum before creating `SubPartDoneContext`. This mapping should live at the call site (likely in `PartExecutor` or wherever the hook is invoked).
