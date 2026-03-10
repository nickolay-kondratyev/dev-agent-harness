# Plan Review: Git Branch Manager

## Executive Summary

The plan is well-structured, thorough, and largely correct. It accurately reflects the codebase conventions (DI, logging, testing patterns) and the ProcessRunner API. There are two issues that need inline correction -- one around `BranchNameBuilder` design (should NOT be a class with companion object for a stateless utility) and one around the `runProcess` return value behavior (includes trailing newline in output). Both are minor and can be fixed inline without a plan iteration cycle.

## Critical Issues (BLOCKERS)

None.

## Major Concerns

### 1. BranchNameBuilder design: Class with companion object is wrong for a stateless pure function

- **Concern**: The plan describes `BranchNameBuilder` as a "class" with a companion object holding `slugify`. But it has no state, no constructor parameters, no logging -- it is a pure function container. Making it a class means callers must instantiate `BranchNameBuilder()` for no reason.
- **Why**: The project convention (per CLAUDE.md) says "Disfavor non-private free-floating functions. Favor cohesive classes; for stateless utilities, use a static class." In Kotlin, a static class for stateless utilities is an `object` (singleton) or a class with a companion object where the methods live in the companion. Given there is no state, the cleanest pattern is to make `BranchNameBuilder` an `object` with `fun build(...)` and `internal fun slugify(...)` as direct members.
- **Suggestion**: Change `BranchNameBuilder` from a `class` to an `object`. This eliminates the need to construct it. The constants and functions all live directly in the object. Usage: `BranchNameBuilder.build(ticketData, tryNumber)`.

### 2. `runProcess` return value includes trailing newline -- plan must account for this

- **Concern**: Looking at `ProcessRunnerImpl.runProcess`, it uses `outputBuilder.appendLine(line)` which appends each line with a newline. For `git rev-parse --abbrev-ref HEAD`, the output will be something like `"main\n"`. The plan correctly mentions "trim the result" in `getCurrentBranch()`, which is fine. However, `createAndCheckout` does not capture or use the return value, so no issue there.
- **Why**: Just confirming the plan handles this correctly -- it does. No action needed.

## Simplification Opportunities (PARETO)

### 1. `workingDir` parameter adds complexity -- consider deferring

- **Current approach**: The plan evolved mid-writing to add an optional `workingDir: Path?` parameter and a `gitCommand()` helper method to GitBranchManagerImpl, primarily to solve the integration test CWD problem.
- **Simpler alternative**: For integration tests, use `git -C <dir>` directly in the test setup commands (via raw `ProcessBuilder`), and have the `GitBranchManagerImpl` use `ProcessRunner` as-is with `git -C` prepended when needed. OR: just accept the `workingDir` parameter as proposed -- it IS genuinely useful for production (the harness may run git against a specific repo directory), and the complexity is minimal (one optional parameter + one 4-line helper).
- **Verdict**: The `workingDir` approach is fine. It is a small addition that serves both testing and future production needs. Keep it.

### 2. Slugify as `internal` function vs testing through `build()`

- **Current approach**: `slugify` is `internal` for direct testing.
- **Assessment**: This is the right call. Testing slugification edge cases (unicode, truncation, empty strings) through `build()` would require constructing `TicketData` objects for each case, which is needless ceremony. `internal` visibility is the standard Kotlin approach for "visible to tests but not public API."

## Minor Suggestions

### 1. `BranchNameBuilder` should NOT validate `ticketData.id` is not blank

The plan adds a `require(ticketData.id.isNotBlank())` check. This is reasonable as a defensive guard, but consider: `TicketParser` already enforces that `id` is present (throws if missing). Adding a second validation here is not harmful but is redundant if the only producer of `TicketData` is `TicketParser`. Keep it for defense-in-depth -- it costs nothing and protects against future direct construction of `TicketData` with invalid data.

### 2. Integration test: default branch name handling

The plan identifies the `main` vs `master` risk correctly. The mitigation suggestion of setting `git config init.defaultBranch main` in the temp repo is the cleanest approach. The implementor should use this. Alternatively, reading the actual branch after init works but is less deterministic in the test assertion.

**Recommended approach for the test**:
```
git init
git config user.email "test@test.com"
git config user.name "Test"
git -C <dir> checkout -b main  // explicit, removes ambiguity
git commit --allow-empty -m "initial"
```
This way the test does not depend on any git global config.

### 3. Test package should be `com.glassthought.chainsaw.core.git`, not `org.example`

Looking at existing tests, the newer tests (TicketParserTest, YamlFrontmatterParserTest, etc.) live in their proper package (`com.glassthought.chainsaw.core.ticket`), while older tests (TmuxSessionManagerIntegTest, etc.) are in `org.example`. The plan correctly places tests in `com.glassthought.chainsaw.core.git`. Good.

### 4. ProcessRunner `runProcess` signature uses `vararg input: String?` (nullable String)

The plan's `gitCommand` helper returns `Array<String>` (non-nullable). When passing to `runProcess(vararg input: String?)`, this will work fine due to Kotlin's type system (String is a subtype of String?). The spread operator `*gitCommand(...)` will work. No issue, just noting for awareness.

### 5. Consider adding a test for branch names with `__` delimiter in the ticket ID

Ticket IDs like `nid_abc__123` would produce branch names like `nid_abc__123____my-feature__try-1` which has four consecutive underscores. This is technically valid but worth a test case to document the behavior explicitly. Low priority.

## Strengths

1. **Thorough exploration**: The plan correctly identified and read the actual `ProcessRunner` API, including the distinction between `runProcess` and `runProcessV2`. The API choice (use `runProcess`) is well-justified.

2. **Test coverage is comprehensive**: The slugify test matrix covers the important edge cases (empty, special chars only, unicode, truncation, trailing hyphen after truncation). The build() tests cover format validation and error cases.

3. **Integration test design is sound**: Using `isIntegTestEnabled()` gating, `@OptIn(ExperimentalKotest::class)`, temp directory with real git repo -- all follow existing project patterns exactly.

4. **Error handling is minimal and correct**: Leveraging `ProcessRunner`'s built-in throw-on-failure rather than catch-and-rethrow follows the project's "don't log and throw" / "let exceptions bubble" convention.

5. **Working directory concern is well-analyzed**: The plan identifies the CWD issue, evaluates multiple solutions, and lands on a clean approach (`git -C` via the `workingDir` parameter).

6. **Implementation order is logical**: Pure logic first (BranchNameBuilder), then tests, then the git wrapper, then integration tests. This allows validating the simpler component before building on it.

7. **Scope is appropriately constrained**: No gold-plating, no premature abstractions. The open questions (git ref validation, checkout existing branch) are correctly deferred.

## Inline Corrections to the Plan

The following corrections should be applied by the implementor:

1. **BranchNameBuilder should be an `object`, not a `class`**. The constants and methods live directly in the object. No companion object needed. Usage: `BranchNameBuilder.build(ticketData, 1)` and `BranchNameBuilder.slugify("title")`.

2. **Integration test setup**: Use explicit `git checkout -b main` after `git init` to avoid dependence on git's default branch name config. Set `user.email` and `user.name` in the temp repo.

3. **GitBranchManager companion factory**: The `standard()` factory should accept `workingDir: Path? = null` as well, so callers can construct via the interface's factory:
   ```
   companion object {
       fun standard(outFactory: OutFactory, processRunner: ProcessRunner, workingDir: Path? = null): GitBranchManager
           = GitBranchManagerImpl(outFactory, processRunner, workingDir)
   }
   ```

## Verdict

- [x] APPROVED WITH MINOR REVISIONS

The plan is solid and ready for implementation with the inline corrections noted above (BranchNameBuilder as `object`, explicit branch init in tests, `workingDir` in factory). These are minor adjustments that do not require a plan iteration cycle. The implementor should apply them directly during implementation.

**PLAN_ITERATION can be skipped.** Proceed to implementation.
