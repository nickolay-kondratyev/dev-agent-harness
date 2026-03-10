# Planner Private Context: Git Branch Manager

## Key Codebase Patterns Discovered

### Interface + Impl Pattern
The project consistently uses interface + implementation in the same file, with a `companion object` factory:
- `TicketParser.standard(outFactory)` returns `TicketParserImpl`
- `Initializer.standard()` returns `InitializerImpl`
- `ProcessRunner.standard(outFactory)` returns `ProcessRunnerImpl`

### Logging Pattern
Every class that logs:
1. Takes `outFactory: OutFactory` as constructor parameter
2. Creates `private val out = outFactory.getOutForClass(ClassName::class)`
3. Uses `out.info("snake_case_message", Val(value, ValType.SPECIFIC_TYPE))` for info
4. Uses `out.debug("snake_case_message") { listOf(Val(...)) }` for debug (lazy lambda)
5. All `Out` methods are suspend

### Test Pattern
- Extends `AsgardDescribeSpec`
- Uses `outFactory` inherited from `AsgardDescribeSpec` (never construct `NoOpOutFactory`)
- BDD: `describe("GIVEN ...")` > `describe("WHEN ...")` > `it("THEN ...")`
- One assert per `it` block
- Integration tests: `@OptIn(ExperimentalKotest::class)`, `.config(isIntegTestEnabled())`
- Integration test support: `org.example.isIntegTestEnabled()` from `integTestSupport.kt`

### ProcessRunner API
From asgardCore (`com.asgard.core.processRunner`):
- `ProcessRunner.runProcess(vararg String?)`: Returns stdout as String, throws RuntimeException on non-zero exit
- `ProcessRunner.runProcessV2(timeout: Duration, vararg String?)`: Returns `ProcessResult(stdout, stderr, exitCode, executionTimeMs)`, throws `ProcessCommandFailedException` on non-zero exit
- Both are `suspend` functions
- Factory: `ProcessRunner.standard(outFactory)`

### Critical Design Decision: Working Directory
`ProcessRunner.runProcess` does NOT support working directory configuration (confirmed by reading `ProcessRunnerImpl`).
For integration tests that need to run git in a temp directory, we need either:
- `git -C <dir>` syntax (preferred -- no new abstractions)
- A `workingDir` parameter on `GitBranchManagerImpl`

Chose to add `workingDir: Path?` to the impl constructor, which enables both test and production usage where the harness may operate on a repo in a different directory.

### Test Package Structure
Tests currently live in two package roots:
- `com.glassthought.chainsaw.core.*` -- matching production package (TicketParserTest, AiOutputStructureTest)
- `org.example` -- legacy location (TmuxSessionManagerIntegTest, InteractiveProcessRunnerTest)

New tests should go in `com.glassthought.chainsaw.core.git` to match the production package.

### TmuxCommandRunner Pattern
`TmuxCommandRunner` is a simple class (not interface) that wraps `ProcessBuilder` directly (not via `ProcessRunner`). It exists because tmux commands need custom output handling (drain and discard stdout). For `GitBranchManager`, using `ProcessRunner` from asgardCore is correct because we want both stdout capture and exit code handling, which `ProcessRunner` provides.

### AiOutputStructure Test Pattern
The cleanest test reference. Uses:
- `TestFixture` data class for setup
- Helper functions for repeated setup
- Clear GIVEN/AND/WHEN/THEN nesting
- Pure path resolution tests (no I/O) + I/O tests for `ensureStructure`

## Decisions Made
1. `BranchNameBuilder` is a class, not interface (pure function, no swap need)
2. `GitBranchManager` IS an interface (wraps external side effect, testability matters)
3. Use `ProcessRunner.runProcess` not `runProcessV2` (simpler, sufficient for fast git ops)
4. Add `workingDir: Path?` to `GitBranchManagerImpl` for test and production flexibility
5. Use `ValType.STRING_USER_AGNOSTIC` for branch names (no custom ValType in asgardCore for V1)
6. Slugify via regex: `[^a-z0-9-]` -> `-`, collapse, trim, truncate, fallback to "untitled"
