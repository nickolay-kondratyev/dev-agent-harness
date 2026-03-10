# Logical Review

## Verdict: READY

## Summary
Adds `BranchNameBuilder` (pure stateless object) and `GitBranchManager` (interface + impl wrapping git CLI via `ProcessRunner`) for building and creating git branches in `{ticketId}__{slug}__try-{N}` format. The implementation is correct, the slugify algorithm handles all edge cases properly, and tests pass. One maintenance concern worth tracking.

## Issues Found

### [SEVERITY: MINOR] Ticket ID Is Not Validated for Git Branch Safety

- **File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilder.kt`
- **Description**: `build()` validates that `ticketData.id` is not blank, but does not validate that it is safe for use in a git branch name. The ticket ID is embedded raw into the branch name without any sanitization. If a ticket ID were to contain characters invalid in git branch names (spaces, `~`, `^`, `:`, `?`, `*`, `[`, `\`, `..`, `@{`, etc.), the failure would come as a cryptic `RuntimeException` from `ProcessRunner` rather than a clear `IllegalArgumentException` at construction time.
- **Why it matters**: The failure mode is deep (git CLI error) rather than early (validation at `BranchNameBuilder.build`). All current ticket IDs in the system use alphanumeric + underscore patterns and are safe, so this is not an active bug. However, if the ticket system ever produces IDs with special characters, the error would be confusing.
- **Suggestion**: Add a validation check in `build()` that the ID only contains git-safe characters (alphanumeric, hyphens, underscores), or document explicitly that the caller is responsible for ensuring the ID is branch-safe. A simple `require(ticketData.id.matches(Regex("[a-zA-Z0-9_-]+")))` would give a clear error early.

### [SEVERITY: MINOR] Ticket ID Containing `__` Would Corrupt the Delimiter Structure

- **File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilder.kt`
- **Description**: The branch name format uses `__` (double underscore) as a structural delimiter between the three components (`{id}__{slug}__try-{N}`). The `id` is passed in raw. If a ticket ID ever contains `__`, the resulting branch name would have more than two `__` delimiters, making any future branch-name parsing ambiguous. For example, `id = "TK__007"` would produce `TK__007__slug__try-1` — indistinguishable from `TK` / `_007` / `slug` parsing.
- **Why it matters**: Currently there is no branch-name parsing code, so this is not an active bug. However, it is a latent trap: any future parser that splits on `__` would silently produce wrong results. The current real ticket IDs (`nid_xxx_E` format) use only single underscores and are safe.
- **Suggestion**: Same as above — validate that the ID does not contain `__` in `build()`, or document the constraint explicitly. This is cheap to add now and prevents a subtle bug when parsing is introduced.

## No Issues Found In

- **Slugify algorithm correctness**: Handles all edge cases correctly — consecutive hyphens collapsed, leading/trailing hyphens trimmed before and after truncation, empty result falls back to "untitled", unicode chars become hyphens. The order of operations (collapse before trim before truncate before trimEnd) is correct.
- **`getCurrentBranch()` output handling**: Uses `.trim()` on the result of `runProcess()`, which correctly strips the trailing newline added by `appendLine` in `ProcessRunnerImpl`.
- **`createAndCheckout()` validation**: Checks `isNotBlank()` before calling git CLI, giving a clear exception for blank names.
- **Integration test isolation**: Each test case creates and tears down its own temp git repository with `try-finally`, ensuring cleanup even on assertion failures.
- **`initGitRepo` determinism**: Explicitly runs `git checkout -b main` instead of relying on `init.defaultBranch` config, so the test is deterministic across systems with different git configurations.
- **Test coverage**: 17 unit tests covering slugify edge cases and build format validation, plus 4 integ tests gated by `isIntegTestEnabled()`. Coverage of the meaningful paths is solid.
- **Logging**: Uses structured `Val/ValType.GIT_BRANCH_NAME` logging correctly, lazy lambda for debug.
- **`ProcessRunner` shared state**: The single `processRunner` instance shared across integ test cases is safe because each test uses an independent temp directory — no shared mutable state.
- **Anchor point**: Created and cross-referenced correctly between the design ticket and the `BranchNameBuilder` KDoc.
