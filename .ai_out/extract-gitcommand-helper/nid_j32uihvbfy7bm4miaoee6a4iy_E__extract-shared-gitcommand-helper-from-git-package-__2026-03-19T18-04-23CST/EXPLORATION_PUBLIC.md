# Exploration: Extract shared gitCommand helper

## Duplicated Code

Three classes in `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/` have identical `private fun gitCommand(vararg args: String): Array<String>`:

1. **GitBranchManagerImpl** (GitBranchManager.kt:92-98)
2. **CommitPerSubPart** (GitCommitStrategy.kt:195-201)
3. **WorkingTreeValidatorImpl** (WorkingTreeValidator.kt:72-78)

All three:
- Take `vararg args: String`
- Return `Array<String>`
- Prepend `"git", "-C", workingDir.toString()` when `workingDir: Path?` is non-null
- Otherwise return `arrayOf("git", *args)`

## Package Structure

All three are in `com.glassthought.shepherd.core.supporting.git`. A shared utility fits naturally in the same package.

## Existing Tests

- `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/CommitPerSubPartTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/git/GitBranchManagerIntegTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCaseImplTest.kt`

## Design Direction

Extract a `GitCommandBuilder` utility class that encapsulates the `workingDir` logic. All three classes inject it instead of duplicating the private method.
