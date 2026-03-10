# Implementation: Git Branch Manager

## Summary

Implemented two components for git branch management in the `com.glassthought.chainsaw.core.git` package:

1. **BranchNameBuilder** -- stateless Kotlin `object` that builds branch names from `TicketData` in the format `{ticketId}__{slugified_title}__try-{N}`.
2. **GitBranchManager** -- interface + `GitBranchManagerImpl` that wraps git CLI commands via `ProcessRunner`.

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilder.kt` | Stateless `object` with `build()` and `slugify()` functions |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/git/GitBranchManager.kt` | Interface + impl for `createAndCheckout` and `getCurrentBranch` |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilderTest.kt` | 17 unit tests covering slugify edge cases and build format |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/git/GitBranchManagerIntegTest.kt` | 4 integration tests with real git repos, gated by `isIntegTestEnabled()` |

## Plan Review Adjustments Applied

1. **BranchNameBuilder as `object`** -- Implemented as a Kotlin `object` (not a class), since it is a stateless pure function container with no constructor parameters.
2. **Integration test git init** -- Uses explicit `git checkout -b main` after `git init` to avoid dependence on system `init.defaultBranch` config.
3. **`GitBranchManager.standard()` factory exposes `workingDir: Path? = null`** -- Matches the impl constructor signature.

## Design Decisions

- **`slugify` is `internal`** -- Allows direct testing of slug edge cases without going through `build()`.
- **Uses `ProcessRunner.runProcess` (not `runProcessV2`)** -- Simpler API, already throws on non-zero exit code. Git operations are fast local commands that don't need timeout protection.
- **Uses `ValType.GIT_BRANCH_NAME`** -- This ValType already exists in asgardCore (discovered during implementation), so no follow-up ticket needed.
- **`workingDir: Path?` parameter** -- Prepends `git -C <dir>` to all commands when set. Serves both integration testing and production use cases.
- **No catch-and-rethrow** -- Exceptions from `ProcessRunner` bubble up naturally per project convention.

## Test Results

- **BranchNameBuilderTest**: 17/17 passed
- **GitBranchManagerIntegTest**: 4/4 passed (with `-PrunIntegTests=true`)
- All existing tests continue to pass.
