# Planner Private Context

## Key Observations From Code Review

### Current Test Framework
- The project currently uses **JUnit 5** with `kotlin-test` assertions (NOT Kotest)
- CLAUDE.md mentions Kotest `DescribeSpec` and `AsgardDescribeSpec`, but the existing tests in this project use plain `@Test` with JUnit 5
- The implementor should follow the **existing pattern** (JUnit 5 `@Test` with GIVEN/WHEN/THEN in method names) for consistency
- Kotest is NOT in `build.gradle.kts` dependencies and adding it would be scope creep

### asgardCore Dependency
- `asgardCore` is resolved from source via composite build (settings.gradle.kts)
- `NoOpOutFactory.INSTANCE` is used in tests
- `SimpleConsoleOutFactory.standard()` is used in production code
- `Val`, `ValType`, and `Out` are the structured logging primitives
- `ValType` enum is in the asgardCore submodule -- adding new entries there is out of scope

### Available ValType Entries (relevant)
- `SHELL_COMMAND` -- good for logging the text being sent as keystrokes
- `STRING_USER_AGNOSTIC` -- good for session names (machine-generated identifiers)
- No `TMUX_SESSION_NAME` exists; use `STRING_USER_AGNOSTIC` for now

### ProcessBuilder Gotcha
The existing `InteractiveProcessRunner` uses `/dev/tty` redirection, which is NOT what we want for tmux. Tmux commands are non-interactive -- we just need to capture exit codes and optionally drain output. This is simpler than the interactive case.

### run.sh Is Minimal
Currently `run.sh` does not forward args. The change is trivial: replace `./app/build/install/app/bin/app` with `./app/build/install/app/bin/app "${@}"`.

### No Existing tmux Code
There is zero tmux infrastructure in the codebase. This is greenfield within the project.

### Package Structure Decision
New code goes into `org.example.tmux` subpackage to keep the root `org.example` package from growing. This follows SRP at the package level.

## Risks / Watch Items
1. **tmux availability in CI**: Integration tests must be gated at the class level. If CI does not have tmux, the tests should be silently skipped (via `Assumptions.assumeTrue` in `@BeforeAll`), not fail.
2. **Session leaks in tests**: Every test that creates a tmux session MUST kill it in cleanup. Use `@AfterEach` with a `try`/`catch` to avoid test pollution.
3. **Output buffer blocking**: ProcessBuilder for tmux commands must drain stdout/stderr to prevent the child process from blocking on a full pipe buffer. This is a subtle but real issue.
4. **Claude startup time**: In the manual end-to-end test, there will be a delay between creating the tmux session with `claude` and Claude being ready to receive input. The demo code should include a delay (acceptable since it is not a test). Automated tests should use `bash` instead of `claude`.
