# Extract shared GitCommandBuilder from git package

## Summary

Extracted the duplicated `gitCommand(vararg args: String): Array<String>` private method from three classes into a shared `GitCommandBuilder` class. All three classes now receive `GitCommandBuilder` via constructor injection instead of duplicating the logic.

## What was done

- Created `GitCommandBuilder` class in the `com.glassthought.shepherd.core.supporting.git` package
- Removed the private `gitCommand()` method from `GitBranchManagerImpl`, `CommitPerSubPart`, and `WorkingTreeValidatorImpl`
- Updated all three class constructors to accept `GitCommandBuilder` instead of `workingDir: Path?`
- Kept all public-facing factory methods (`GitBranchManager.standard()`, `GitCommitStrategy.commitPerSubPart()`, `WorkingTreeValidator.standard()`) unchanged — they still accept `workingDir: Path?` and construct `GitCommandBuilder` internally
- Updated `CommitPerSubPartTest` to use the new constructor parameter
- Updated `detekt-baseline.xml` to match renamed spread operator call sites
- Added `GitCommandBuilderTest` with BDD-style tests

## Files created

- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommandBuilder.kt` — shared utility class
- `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommandBuilderTest.kt` — unit tests

## Files modified

- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt` — constructor takes `GitCommandBuilder`, removed private `gitCommand()`
- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt` — constructor takes `GitCommandBuilder` (with default), removed private `gitCommand()`
- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/WorkingTreeValidator.kt` — constructor takes `GitCommandBuilder`, removed private `gitCommand()`
- `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/CommitPerSubPartTest.kt` — updated `workingDir` parameter to `gitCommandBuilder`
- `detekt-baseline.xml` — updated 3 SpreadOperator entries to match renamed method calls

## Tests

All tests pass (`./test.sh` exit code 0, BUILD SUCCESSFUL).

## Design decisions

- `GitCommandBuilder` uses `build()` instead of `gitCommand()` as the method name — clearer when called as `gitCommandBuilder.build(...)`.
- `CommitPerSubPart` uses a default parameter `gitCommandBuilder: GitCommandBuilder = GitCommandBuilder()` to keep test construction simple (most tests don't care about workingDir).
- Public interface factory methods are unchanged — no external API impact.
