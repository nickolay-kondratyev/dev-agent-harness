# Code Review: GitCommitStrategy + CommitPerSubPart

## Summary

Implements `GitCommitStrategy` interface with a single `onSubPartDone` hook and its V1 `CommitPerSubPart` implementation. The implementation follows the established `GitBranchManager` pattern, delegates git failures correctly, and handles the empty-diff skip case. Tests cover happy path, empty diff, git add failure, git commit failure, workingDir prefixing, no-reviewer message format, and PI agent type.

**Overall assessment: GOOD. One important issue, a couple of suggestions.**

All tests pass (`:app:test` exit 0, including detekt).

---

## No CRITICAL Issues

No security, correctness, or data-loss issues found.

---

## IMPORTANT Issues

### 1. `stageAll` continues execution after `handleGitFailure` returns normally

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt` lines 133-143

```kotlin
private suspend fun stageAll(context: SubPartDoneContext) {
    val command = gitCommand("add", "-A")
    try {
        processRunner.runProcess(*command)
    } catch (e: Exception) {
        gitOperationFailureUseCase.handleGitFailure(
            gitCommand = command.toList(),
            errorOutput = e.message ?: "unknown",
            context = toGitFailureContext(context),
        )
    }
}
```

`GitOperationFailureUseCase.handleGitFailure` can **return normally** when index.lock recovery succeeds (see `GitOperationFailureUseCaseImpl` line 103). When that happens, `stageAll` returns and `onSubPartDone` continues to `hasStagedChanges()`. This is actually correct behavior for `stageAll` -- the retry inside `handleGitFailure` retries the same `git add -A`, so if it succeeds, continuing is fine.

However, the same pattern on `commit()` (lines 166-186) is more subtle. If `git commit` fails with an index.lock error and `handleGitFailure` recovers by retrying `git commit` internally, then `commit()` returns normally and `onSubPartDone` logs `"commit_created"`. This works but is fragile -- the recovery retry inside `handleGitFailure` replays the full git command, so the commit effectively happens inside the recovery path. The subsequent log `"commit_created"` is correct.

**The real concern**: The `handleGitFailure` retries the raw `gitCommand` list, which for commit includes the `--author` and `-m` args. If the retry command doesn't match exactly what was originally sent (e.g., different message escaping), the retry would fail. This is fine today but is worth a comment explaining that `handleGitFailure` retries the exact same command.

**Recommendation**: Add a brief comment in `commit()` noting that `handleGitFailure` may return normally after successful retry. This makes the control flow explicit for future readers.

---

## Suggestions

### 1. DRY: Extract shared `gitCommand` helper

The `gitCommand` private method in `CommitPerSubPart` (line 191-197) is a copy-paste duplicate of the same method in `GitBranchManagerImpl` (line 92-98). Both are identical:

```kotlin
private fun gitCommand(vararg args: String): Array<String> {
    return if (workingDir != null) {
        arrayOf("git", "-C", workingDir.toString(), *args)
    } else {
        arrayOf("git", *args)
    }
}
```

Consider extracting this into a shared utility (e.g., a `GitCommandBuilder` or a top-level internal function in the git package). This is a follow-up ticket item, not a blocker -- the duplication is contained and mechanical.

### 2. Test DRY: Strategy construction is duplicated across every `it` block

Every test case creates its own `CommitPerSubPart` instance with the same constructor args. The `createRunnerWith*` factory methods DRY the runner setup, but the strategy construction is repeated ~12 times. Consider extracting a helper:

```kotlin
private fun createStrategy(
    runner: RecordingFakeProcessRunner,
    failureUseCase: FakeGitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
    workingDir: Path? = null,
): CommitPerSubPart = CommitPerSubPart(
    outFactory = outFactory,
    processRunner = runner,
    gitOperationFailureUseCase = failureUseCase,
    hostUsername = HOST_USERNAME,
    gitUserEmail = GIT_USER_EMAIL,
    workingDir = workingDir,
)
```

That said, per CLAUDE.md, DRY is "much less important in tests" -- this is optional.

### 3. `RecordingFakeProcessRunner` could be shared

This fake is useful beyond this single test file. If other git-related tests need command recording, consider promoting it to a shared test utility. Follow-up item.

### 4. `CommitPerSubPart` visibility

The class is `public` but only constructed via the companion factory `GitCommitStrategy.commitPerSubPart(...)`. Consider making it `internal` to enforce construction through the factory. The test file is in the same package so it would still have access.

---

## Spec Compliance Check

| Spec requirement | Status |
|---|---|
| `onSubPartDone` hook fires after sub-part signals done | Implemented |
| `git add -A` first | Implemented, verified in test |
| `git diff --cached --quiet` empty check | Implemented, verified in test |
| Empty diff skips commit | Implemented, verified in test |
| Commit message via CommitMessageBuilder | Implemented, verified in test |
| Author via CommitAuthorBuilder | Implemented, verified in test |
| `git commit --author` | Implemented, verified in test |
| Git failure delegates to GitOperationFailureUseCase | Implemented, verified for both add and commit |
| SubPartDoneContext contains all hook context fields (part name, sub-part name, role, result, iteration, agent type, model) | Implemented |
| Email "stays as-is" from git config | Implemented via `gitUserEmail` constructor param read from config at wiring time |

---

## Documentation Updates Needed

None required. The implementation file has adequate KDoc, the spec is already written, and the anchor point ref is correctly placed.

---

## Verdict

**Approve with minor suggestions.** The implementation is clean, follows established patterns, matches the spec, and has thorough test coverage. The important issue (control flow after `handleGitFailure` returns normally) is technically correct but would benefit from a clarifying comment.
