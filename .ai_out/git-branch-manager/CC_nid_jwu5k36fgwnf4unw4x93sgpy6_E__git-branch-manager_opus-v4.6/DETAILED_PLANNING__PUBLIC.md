# Detailed Implementation Plan: Git Branch Manager

## 1. Problem Understanding

**Goal**: Implement two components for git branch management in Chainsaw:

1. **BranchNameBuilder** -- a pure function that builds branch names from `TicketData` in the format `{TICKET_ID}__{slugified_title}__try-{N}`.
2. **GitBranchManager** -- an interface + implementation that wraps git CLI commands (`git checkout -b`, `git rev-parse`) via the asgardCore `ProcessRunner`.

**Constraints**:
- Package: `com.glassthought.chainsaw.core.git`
- Constructor injection, `OutFactory` logging, no singletons
- Fail-fast on git errors (non-zero exit codes)
- V1: truncate long titles (no LLM compression)
- No `build.gradle.kts` changes needed (all deps already present)

**Assumptions**:
- `ProcessRunner` from asgardCore is the correct abstraction for git CLI commands (confirmed by reading the interface -- it provides `runProcess(vararg)` which throws on non-zero exit code, and `runProcessV2(timeout, vararg)` which returns structured `ProcessResult`)
- Branch names should be valid git ref names (no spaces, no `..`, no `~`, no `^`, no `:`, no `\`)
- The slug max length of 60 chars applies to the **slug portion only**, not the entire branch name

## 2. High-Level Architecture

```
TicketData ──> BranchNameBuilder.build(ticketData, tryNumber) ──> branchName (String)

branchName ──> GitBranchManager.createAndCheckout(branchName) ──> Unit (or throws)
           ──> GitBranchManager.getCurrentBranch() ──> String
```

**Component Relationships**:
- `BranchNameBuilder` depends on nothing (pure function, stateless utility)
- `GitBranchManager` depends on `ProcessRunner` (from asgardCore) and `OutFactory` (for logging)
- Both live in `com.glassthought.chainsaw.core.git`

**Data Flow**:
- `BranchNameBuilder` is a stateless class with a companion factory (following `TicketParser.standard(outFactory)` pattern, but since BranchNameBuilder is pure with no logging, it can use a simpler pattern)
- `GitBranchManager` receives `ProcessRunner` and `OutFactory` via constructor injection

## 3. Files to Create

### Production Code

| # | File Path | Description |
|---|-----------|-------------|
| 1 | `app/src/main/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilder.kt` | Pure function class for building branch names from TicketData |
| 2 | `app/src/main/kotlin/com/glassthought/chainsaw/core/git/GitBranchManager.kt` | Interface + impl for git branch operations |

### Test Code

| # | File Path | Description |
|---|-----------|-------------|
| 3 | `app/src/test/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilderTest.kt` | Unit tests for slug generation and branch name formatting |
| 4 | `app/src/test/kotlin/com/glassthought/chainsaw/core/git/GitBranchManagerIntegTest.kt` | Integration tests for actual git operations |

## 4. Implementation Phases

### Phase 1: BranchNameBuilder (Pure Logic)

**Goal**: Implement the branch name construction from TicketData.

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilder.kt`

#### Interface & Class Design

```
BranchNameBuilder (class, stateless)
  +-- build(ticketData: TicketData, tryNumber: Int): String
  +-- companion object
      +-- private const val DELIMITER = "__"
      +-- private const val MAX_SLUG_LENGTH = 60
      +-- private const val TRY_PREFIX = "try-"
      +-- internal fun slugify(title: String): String   // visible for testing
```

#### Method: `build(ticketData: TicketData, tryNumber: Int): String`

**Behavior**:
1. Validate `tryNumber >= 1` (fail-fast with `require`)
2. Validate `ticketData.id` is not blank (fail-fast with `require`)
3. Slugify the title
4. Concatenate: `${ticketData.id}${DELIMITER}${slug}${DELIMITER}${TRY_PREFIX}${tryNumber}`
5. Return the branch name

#### Method: `slugify(title: String): String` (companion, `internal` visibility for testing)

**Slugify Algorithm** (pseudocode):
```
1. Lowercase the input
2. Replace any character that is NOT alphanumeric or hyphen with a hyphen
3. Collapse consecutive hyphens into a single hyphen
4. Trim leading/trailing hyphens
5. Truncate to MAX_SLUG_LENGTH characters
6. Trim any trailing hyphen introduced by truncation
7. If result is empty after all processing, return "untitled"
```

**Design Decision**: `slugify` is `internal` (not `private`) so tests can verify slugification edge cases directly without going through `build()`. This follows the pattern of testing pure utility logic in isolation. It lives in a `companion object` since it is stateless.

**Design Decision**: `BranchNameBuilder` is a simple class (not an interface) because there is no foreseeable need to swap implementations -- it is a pure function with deterministic output. This follows YAGNI. If V2 adds LLM compression, it would be a different builder (OCP -- extend, don't modify).

### Phase 2: BranchNameBuilder Tests

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/git/BranchNameBuilderTest.kt`

Extends `AsgardDescribeSpec`. BDD structure with GIVEN/WHEN/THEN.

#### Test Cases for `slugify`:

| GIVEN | WHEN | THEN |
|-------|------|------|
| A simple title "My Feature" | slugify is called | Returns "my-feature" |
| A title with special chars "Fix: bug #123!" | slugify is called | Returns "fix-bug-123" |
| A title with consecutive spaces "fix   the    bug" | slugify is called | Returns "fix-the-bug" |
| A title with leading/trailing special chars "---hello---" | slugify is called | Returns "hello" |
| A title exceeding 60 chars | slugify is called | Result length is <= 60 |
| A title exceeding 60 chars | slugify is called | Result does not end with a hyphen (trimmed after truncation) |
| An empty string "" | slugify is called | Returns "untitled" |
| A string of only special chars "!@#$%^" | slugify is called | Returns "untitled" |
| A title with unicode "cafe\u0301 latte" | slugify is called | Non-ascii chars become hyphens, result is "caf-latte" or similar |

#### Test Cases for `build`:

| GIVEN | WHEN | THEN |
|-------|------|------|
| TicketData with id="TK-001", title="My Feature", tryNumber=1 | build is called | Returns "TK-001__my-feature__try-1" |
| TicketData with id="TK-001", title="My Feature", tryNumber=3 | build is called | Returns "TK-001__my-feature__try-3" |
| TicketData with tryNumber=0 | build is called | Throws IllegalArgumentException |
| TicketData with tryNumber=-1 | build is called | Throws IllegalArgumentException |
| TicketData with blank id | build is called | Throws IllegalArgumentException |
| TicketData with long title and id="nid_abc123" | build is called | Format is `{id}__{slug<=60}__{try-N}` |

### Phase 3: GitBranchManager (Git CLI Wrapper)

**Goal**: Implement the git branch operations via ProcessRunner.

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/git/GitBranchManager.kt`

#### Interface & Class Design

```
interface GitBranchManager {
    suspend fun createAndCheckout(branchName: String)
    suspend fun getCurrentBranch(): String

    companion object {
        fun standard(outFactory: OutFactory, processRunner: ProcessRunner): GitBranchManager
            = GitBranchManagerImpl(outFactory, processRunner)
    }
}

class GitBranchManagerImpl(
    outFactory: OutFactory,
    private val processRunner: ProcessRunner,
) : GitBranchManager {
    private val out = outFactory.getOutForClass(GitBranchManagerImpl::class)
    ...
}
```

#### Method: `createAndCheckout(branchName: String)`

**Behavior**:
1. Validate `branchName` is not blank (fail-fast with `require`)
2. Log `creating_and_checking_out_branch` at info level with the branch name
3. Call `processRunner.runProcess("git", "checkout", "-b", branchName)`
   - `ProcessRunner.runProcess` already throws `RuntimeException` on non-zero exit code -- fail-fast for free
4. Log `branch_created_and_checked_out` at info level

**Design Decision**: Use `runProcess` (not `runProcessV2`) because we do not need a timeout for `git checkout -b` -- it is a fast local operation. The simpler API is preferable. `runProcess` already throws on non-zero exit, which gives us fail-fast behavior.

#### Method: `getCurrentBranch(): String`

**Behavior**:
1. Log `getting_current_branch` at debug level (lazy form)
2. Call `processRunner.runProcess("git", "rev-parse", "--abbrev-ref", "HEAD")`
3. Trim the result (output includes trailing newline)
4. Log `current_branch_resolved` at info level with the branch name
5. Return the trimmed branch name

### Phase 4: GitBranchManager Integration Tests

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/git/GitBranchManagerIntegTest.kt`

Gated with `.config(isIntegTestEnabled())`. Annotated with `@OptIn(ExperimentalKotest::class)`.

**Setup**: Each test creates a temporary directory, initializes a git repo (`git init`), creates an initial commit, and provides a `GitBranchManager` instance. Cleanup removes the temp directory.

#### Test Cases:

| GIVEN | WHEN | THEN |
|-------|------|------|
| A fresh git repo on "main" | getCurrentBranch is called | Returns "main" (or "master" depending on git config -- use the actual default) |
| A fresh git repo | createAndCheckout("feature__test__try-1") is called | getCurrentBranch returns "feature__test__try-1" |
| A fresh git repo | createAndCheckout with an already-existing branch name | Throws RuntimeException (git checkout -b fails for existing branch) |
| A fresh git repo | createAndCheckout("") is called | Throws IllegalArgumentException |

**Important**: Integration tests need a real git repo. Create via `ProcessRunner.runProcess("git", "init", tempDir.toString())` and set up an initial commit so branches can be created. Use `ProcessBuilder` directly (or the same `ProcessRunner`) for setup. Set `git config user.email` and `git config user.name` in the temp repo to avoid git config errors in CI.

## 5. Technical Considerations

### Slugify Regex

The core transformation can be done with two regex operations:
1. `[^a-z0-9-]` -> replace with `-` (catches all non-alphanumeric, non-hyphen)
2. `-{2,}` -> replace with `-` (collapse consecutive hyphens)

This is simple and covers all edge cases including unicode, whitespace, and special characters.

### ProcessRunner API Choice

- **`runProcess(vararg String)`**: Returns stdout as String, throws on non-zero exit. Suitable for `git checkout -b` and `git rev-parse`. This is the simpler API.
- **`runProcessV2(timeout, vararg String)`**: Returns `ProcessResult` with stdout, stderr, exitCode, timing. Throws `ProcessCommandFailedException` on non-zero exit and `ProcessCommandTimeoutException` on timeout. Overkill for fast local git commands in V1.

**Recommendation**: Use `runProcess` for both methods. Simpler, already throws on failure, and git operations are instantaneous. If timeout protection is needed later, switching to `runProcessV2` is trivial.

### Working Directory

`ProcessRunner.runProcess` does not set a working directory -- it inherits the JVM's working directory. This is correct for Chainsaw because git commands should run against the current working directory (the repo root). If a future need arises to run git in a different directory, we can add a `workingDir: Path` parameter to `GitBranchManager.createAndCheckout`.

**Risk**: In integration tests, we need to run git in the temp directory, not the JVM's CWD. The `ProcessRunner` from asgardCore does not support setting CWD. For integration tests, use `ProcessBuilder` directly with `.directory(tempDir)` for setup steps, and pass `-C tempDir` to git commands, or use a test-specific `ProcessRunner` that sets the working directory.

**Mitigation**: For integration tests, create a `TestProcessRunner` that wraps a `ProcessBuilder` with a configurable working directory, OR use `git -C <dir>` syntax which allows specifying the repo directory as an argument. The `-C` approach is cleaner and avoids creating a test-only `ProcessRunner`.

Revised approach for `GitBranchManagerImpl`: Accept an optional `workingDir: Path?` parameter. When provided, prepend `"-C", workingDir.toString()` to all git commands. This is useful both for tests and for production code (where the harness may run git against a specific repo directory).

```
class GitBranchManagerImpl(
    outFactory: OutFactory,
    private val processRunner: ProcessRunner,
    private val workingDir: Path? = null,
) : GitBranchManager
```

Helper method to build git commands:
```
private fun gitCommand(vararg args: String): Array<String> {
    return if (workingDir != null) {
        arrayOf("git", "-C", workingDir.toString(), *args)
    } else {
        arrayOf("git", *args)
    }
}
```

### Error Handling

- `BranchNameBuilder`: Uses `require` for input validation (throws `IllegalArgumentException`)
- `GitBranchManagerImpl`: Relies on `ProcessRunner.runProcess` throwing `RuntimeException` on non-zero exit code. The exception message from `ProcessRunnerImpl` already includes the command and output, which is sufficient for diagnosing failures.
- No catch-and-rethrow -- let exceptions bubble up per project convention.

### ValType for Logging

Based on existing usage, use:
- `ValType.STRING_USER_AGNOSTIC` for branch names (they contain ticket IDs and slugified titles, not user-specific data)
- `ValType.SHELL_COMMAND` if logging the full git command

If `ValType.GIT_BRANCH_NAME` does not exist, create it as a new `ValType` entry. However, since `ValType` is in asgardCore (external dependency), use `ValType.STRING_USER_AGNOSTIC` for V1. **Call out**: A follow-up ticket could add `GIT_BRANCH_NAME` to asgardCore's `ValType` enum.

## 6. Implementation Order

1. **BranchNameBuilder** (`BranchNameBuilder.kt`) -- no dependencies, pure logic
2. **BranchNameBuilderTest** (`BranchNameBuilderTest.kt`) -- validate all slug and build cases
3. **Run tests** -- ensure all BranchNameBuilder tests pass
4. **GitBranchManager** (`GitBranchManager.kt`) -- interface + impl
5. **GitBranchManagerIntegTest** (`GitBranchManagerIntegTest.kt`) -- real git operations
6. **Run integration tests** -- `./gradlew :app:test -PrunIntegTests=true`

## 7. Verification Criteria

### BranchNameBuilder
- All unit tests pass
- `slugify("My Cool Feature!!!")` returns `"my-cool-feature"`
- A 100-char title produces a slug of <= 60 chars
- Branch name format matches `{id}__{slug}__try-{N}` exactly
- Empty/blank inputs throw `IllegalArgumentException`

### GitBranchManager
- `getCurrentBranch()` returns trimmed branch name
- `createAndCheckout("new-branch")` creates and switches to "new-branch"
- Creating an already-existing branch throws
- Blank branch name throws `IllegalArgumentException`

## 8. Risks and Concerns

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `ProcessRunner` CWD issue in integ tests | High | Use `git -C <dir>` syntax or `workingDir` parameter |
| Git not installed in CI | Low | Integration tests are gated with `isIntegTestEnabled()` |
| `ValType.GIT_BRANCH_NAME` missing | Certain | Use `ValType.STRING_USER_AGNOSTIC` for V1, create follow-up ticket |
| Default branch name varies (`main` vs `master`) | Medium | In integ tests, explicitly set `git config init.defaultBranch main` in temp repo OR read actual branch after init |
| Unicode title edge cases | Low | Regex `[^a-z0-9-]` catches all non-ASCII -- they become hyphens |

## 9. Open Questions

1. **Should `BranchNameBuilder` validate that the resulting branch name is a valid git ref?** Recommendation: No, for V1. The slugify + delimiter format inherently produces valid refs (no spaces, no `..`, no problematic characters). Git will reject invalid refs at `createAndCheckout` time anyway.

2. **Should `GitBranchManager` also support `checkout(existingBranch)`?** Not in scope per task description. Can be added later as a separate method without modifying existing code (OCP).
