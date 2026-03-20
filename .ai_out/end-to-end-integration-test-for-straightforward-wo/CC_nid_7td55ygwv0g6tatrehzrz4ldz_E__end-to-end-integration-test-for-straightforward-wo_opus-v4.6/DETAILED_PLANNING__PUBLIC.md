# Detailed Plan: E2E Integration Test for Straightforward Workflow

## 1. Problem Understanding

**Goal**: Create an integration test that runs the actual `app` binary as a subprocess, exercises the full straightforward workflow (spawn agent, handshake, instruction delivery, agent execution, done signal, review, exit), and verifies that the agent produced the expected output.

**Key constraints**:
- The binary calls `System.exit()` on completion, so it MUST be run as a subprocess via `ProcessBuilder` -- not in-process.
- The binary requires a Docker environment (`/.dockerenv`), required env vars (`HOST_USERNAME`, `TICKET_SHEPHERD_AGENTS_DIR`, `MY_ENV`, `AI_MODEL__ZAI__FAST`), and `TICKET_SHEPHERD_SERVER_PORT`.
- The binary creates a git branch, validates working tree cleanliness, and commits agent output.
- GLM (Z.AI) must be used for the agent to avoid Anthropic API costs.
- The test must be gated with `isIntegTestEnabled()`.

**Critical architectural insight**: Unlike existing integration tests (`AgentFacadeImplIntegTest`, `SelfCompactionIntegTest`) which wire internal components and start their own HTTP server, this E2E test runs the **entire binary** as a black box. The binary starts its OWN HTTP server, initializes its OWN context, and manages everything internally. The test only controls: (a) environment variables, (b) the ticket file, (c) the working directory, and (d) process lifecycle.

## 2. High-Level Architecture

```
Test Process (JVM)                       Binary Subprocess
┌─────────────────────┐                 ┌────────────────────────────┐
│ StraightforwardE2E  │                 │ app binary                 │
│ IntegTest           │                 │                            │
│                     │  ProcessBuilder │ EnvironmentValidator       │
│ 1. Create temp dir  │ ──────────────> │ ContextInitializer         │
│ 2. git init         │                 │ ShepherdServer (port X)    │
│ 3. Create ticket    │                 │ TicketShepherdCreator      │
│ 4. Start process    │                 │ TicketShepherd.run()       │
│ 5. Wait for exit    │                 │   └─ PartExecutor          │
│ 6. Verify outputs   │                 │       ├─ spawn doer (tmux) │
│ 7. Cleanup          │                 │       ├─ send instructions │
│                     │  exit code 0    │       ├─ spawn reviewer    │
│ <────────────────── │ <────────────── │       └─ reviewer PASS     │
│                     │                 │ System.exit(0)             │
└─────────────────────┘                 └────────────────────────────┘
```

**The test does NOT extend `SharedContextDescribeSpec`** because `SharedContextDescribeSpec` provides a shared `ShepherdContext` for tests that wire internal components. This test runs the binary externally, so it needs `AsgardDescribeSpec` directly (or even just Kotest's DescribeSpec with integ gate). However, since the binary internally uses `ContextInitializer.standard()` (production mode), the test must ensure the binary's own context initialization works correctly with the environment variables provided.

**Wait -- the binary uses `ContextInitializer.standard()`** which does NOT enable GLM. The binary reads `TICKET_SHEPHERD_SERVER_PORT` from env vars and uses the real Anthropic API. To use GLM, the test must set the GLM env vars (`ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, etc.) in the subprocess environment so the `claude` CLI process (spawned by the binary via tmux) picks them up.

However, looking more carefully at `ContextInitializerImpl`: when `glmEnabled = false` (production), it still creates a `ClaudeCodeAdapter` without `GlmConfig`. The GLM env vars would need to be present in the tmux session environment for the agent to use GLM. Since `ClaudeCodeAdapter.buildStartCommand()` only exports GLM env vars when a `GlmConfig` is provided, we cannot rely on just setting env vars on the binary process.

**Revised understanding**: The binary's `ContextInitializer.standard()` does NOT inject GLM. For the E2E test to use GLM, we have two options:
1. Set `ANTHROPIC_BASE_URL` and `ANTHROPIC_AUTH_TOKEN` as env vars on the subprocess, and hope the `claude` CLI picks them up from the parent environment (tmux inherits parent env).
2. Accept that this test uses the real Anthropic API (costly).

Let me verify option 1 by examining how `ClaudeCodeAdapter` builds the command.

## 2a. GLM Strategy -- CRITICAL DECISION

After examining `ClaudeCodeAdapter.buildStartCommand()` (explored in EXPLORATION_PUBLIC.md), the adapter explicitly exports env vars into the bash command string. When `GlmConfig` is null (production mode), GLM env vars are NOT exported. However, `claude` CLI inherits the tmux session's environment, which inherits from the process that created the tmux session.

**The tmux session is created by the binary process.** If we set `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, and the model alias env vars on the binary's `ProcessBuilder` environment, they will be inherited by tmux, and then by the `claude` CLI process.

BUT: the `ClaudeCodeAdapter.buildStartCommand()` uses `unset CLAUDECODE` but does NOT unset `ANTHROPIC_BASE_URL` or `ANTHROPIC_AUTH_TOKEN`. So if these are in the environment, `claude` CLI should pick them up.

**Decision: Set GLM env vars on the ProcessBuilder environment.** The `claude` CLI process will inherit them via tmux environment inheritance. This avoids modifying the binary or its production ContextInitializer. The env vars `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC`, and model aliases will be set.

## 3. Implementation Phases

### Phase 1: Test File Setup

**Goal**: Create the test class file in the correct location with proper structure.

**File location**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt`

**Components**:
- New package `com.glassthought.shepherd.integtest.e2e` to distinguish from component-level integ tests
- Extends `AsgardDescribeSpec` (NOT `SharedContextDescribeSpec` -- we do not need internal ShepherdContext)
- Uses `@OptIn(ExperimentalKotest::class)` and gates with `isIntegTestEnabled()`

**Key Steps**:
1. Create directory `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/`
2. Create `StraightforwardWorkflowE2EIntegTest.kt`
3. Annotate with `@OptIn(ExperimentalKotest::class)`
4. Gate entire describe block with `.config(isIntegTestEnabled())`

### Phase 2: Temporary Git Repository Setup

**Goal**: Create an isolated git repo with a ticket file that the binary can operate on.

**Key Steps**:
1. Create a temp directory under `.tmp/e2e-straightforward-<timestamp>/`
2. Run `git init` in the temp directory
3. Create a minimal `config/workflows/straightforward.json` (copy from the real project, or symlink)
4. Create a ticket file with valid YAML frontmatter:
   ```yaml
   ---
   id: e2e-test-straightforward
   title: "E2E Test: Write hello-world.sh"
   status: in_progress
   ---

   # Task

   Create a file called `hello-world.sh` that prints "Hello, World!" to stdout.
   The file should be executable.
   ```
5. `git add .` and `git commit -m "Initial commit"` in the temp dir (working tree must be clean)
6. Create a `CLAUDE.md` in the temp dir with minimal agent instructions (so the agent knows what to do)

**IMPORTANT**: The binary runs `WorkingTreeValidator.validate()` which checks that the working tree is clean. The binary also runs `git` operations (create branch, commit). The temp dir MUST be a valid git repo with a clean working tree.

**IMPORTANT**: The binary resolves `config/workflows/straightforward.json` relative to `repoRoot` (which is `System.getProperty("user.dir")`). Since the binary's working directory will be set to the temp dir, we must copy the workflow file there.

### Phase 3: Environment Variables

**Goal**: Configure all required environment variables for the subprocess.

The binary validates these env vars at startup:
1. `HOST_USERNAME` -- can be set to any value (e.g., "e2e-test")
2. `TICKET_SHEPHERD_AGENTS_DIR` -- must point to agent role definitions. Copy from the real project OR point to the real project's config dir.
3. `MY_ENV` -- must point to a directory containing `.secrets/Z_AI_GLM_API_TOKEN`. Use the real `MY_ENV` from the current environment.
4. `AI_MODEL__ZAI__FAST` -- model identifier. Read from current environment.
5. `TICKET_SHEPHERD_SERVER_PORT` -- dynamically allocate via `ServerSocket(0).use { it.localPort }`

Additionally, for GLM:
6. `ANTHROPIC_BASE_URL` = `https://api.z.ai/api/anthropic`
7. `ANTHROPIC_AUTH_TOKEN` = read from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN`
8. `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC` = `1`
9. `ANTHROPIC_DEFAULT_OPUS_MODEL` = `glm-5` (or read from env)
10. `ANTHROPIC_DEFAULT_SONNET_MODEL` = `glm-5`
11. `ANTHROPIC_DEFAULT_HAIKU_MODEL` = `glm-4-flash`

**Docker check bypass**: The binary checks for `/.dockerenv`. If running outside Docker, the test will fail at `EnvironmentValidator.standard().validate()`. Two options:
- Run the test inside Docker (likely the CI environment already is)
- The test should document this requirement

**Key Steps**:
1. Read `MY_ENV` from current process environment
2. Read `Z_AI_GLM_API_TOKEN` from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN`
3. Allocate a dynamic port
4. Build environment map for ProcessBuilder
5. Copy `TICKET_SHEPHERD_AGENTS_DIR` from current environment (it must point to a valid directory with role `.md` files)

### Phase 4: Binary Build and Process Execution

**Goal**: Build the binary (if not already built) and run it with correct arguments.

**Key Steps**:
1. Ensure binary is built. The test should depend on `installDist` Gradle task. Options:
   - Add a Gradle task dependency so `installDist` runs before tests
   - Run `ProcessBuilder("./gradlew", ":app:installDist")` as a setup step
   - Rely on CI/developer to run `installDist` before tests (document this)

   **Recommendation**: Run `./gradlew :app:installDist` in a `beforeSpec` block or as a Gradle task dependency. The safest approach is to add a Gradle task dependency in `build.gradle.kts`:
   ```kotlin
   tasks.test {
       dependsOn(":app:installDist") // ensure binary is built before tests
   }
   ```
   This is clean and automatic. However, it may slow down unit test runs. Since integ tests are gated, this is acceptable.

   **Simpler alternative**: Just ensure the binary path exists and fail hard if not. Document that developers must run `./gradlew :app:installDist` before integ tests.

2. Construct ProcessBuilder:
   ```
   ProcessBuilder(
       binaryPath,
       "run",
       "--workflow", "straightforward",
       "--ticket", ticketPath,
       "--iteration-max", "1",
   )
   ```
3. Set working directory to the temp git repo
4. Set environment variables
5. Redirect stdout/stderr to files in `.tmp/` for debugging
6. Start process
7. Wait with timeout (10 minutes -- GLM agents are slow)
8. Capture exit code

### Phase 5: Verification Strategy

**Goal**: Assert that the workflow completed successfully and produced expected output.

**Assertions** (each in its own `it` block per BDD standards):

1. **Exit code is 0** -- the binary exits with 0 on success.
2. **hello-world.sh exists** -- the agent was instructed to create this file in the working directory.
3. **hello-world.sh is correct** -- should contain "Hello, World!" when executed or when read.
4. **Git branch was created** -- the binary creates a feature branch. Verify with `git branch` in the temp dir.
5. **Git commits exist** -- the binary commits agent work. Verify with `git log` in the temp dir.
6. **.ai_out/ directory exists** -- the binary creates this structure.

**Practical consideration**: Assertions 2 and 3 depend on the agent actually producing the correct output. With GLM (not a real Anthropic model), the output quality is less predictable. The test should be resilient to minor variations:
- Assert that `hello-world.sh` exists (primary)
- Assert that it contains "hello" or "Hello" case-insensitively (secondary, softer)
- Assert exit code 0 (most critical -- proves the full workflow completed)

### Phase 6: Cleanup

**Goal**: Clean up all resources regardless of test outcome.

**Key Steps** (in `afterSpec`):
1. Kill any remaining tmux sessions that were created by the binary. The binary creates sessions with predictable naming. Use `tmux kill-server` as a nuclear option, or `tmux list-sessions` + `tmux kill-session -t <name>` for targeted cleanup.

   **Better approach**: Since the binary may have created sessions we do not know the names of, and since other integ tests may also be using tmux, we should NOT kill all sessions. Instead:
   - The binary itself kills sessions on successful exit (`allSessionsKiller.killAllSessions()` in `TicketShepherd.run()`)
   - If the binary crashed, sessions may linger. Record tmux sessions before/after the test and clean up new ones.

   **Simplest approach**: List tmux sessions before the test, list after, kill the delta.

2. Delete the temp directory recursively.
3. Delete any subprocess stdout/stderr log files.

## 4. Technical Considerations

### 4a. Docker Requirement

The `EnvironmentValidator.standard()` checks for `/.dockerenv`. This E2E test can only run inside a Docker container. This is consistent with the project's design (agents use `--dangerously-skip-permissions`). The `isIntegTestEnabled()` gate already implies an appropriate environment.

### 4b. Port Allocation Race Condition

`ServerSocket(0).use { it.localPort }` allocates a port, but there is a TOCTOU window before the binary binds to it. The same pattern is used in existing integ tests and is acceptable.

### 4c. Workflow Config Resolution

The binary resolves `config/workflows/straightforward.json` relative to `System.getProperty("user.dir")`. Since the ProcessBuilder's working directory is set to the temp git repo, the workflow file must exist at `<tempDir>/config/workflows/straightforward.json`.

**Copy strategy**: Copy the real workflow file from the project root to the temp dir during setup.

### 4d. Agent Role Definitions

`TICKET_SHEPHERD_AGENTS_DIR` must point to a directory with role `.md` files (e.g., `IMPLEMENTATION_WITH_SELF_PLAN.md`, `IMPLEMENTATION_REVIEWER.md`). The test should point this to the real project's agents dir by reading the env var from the current environment.

### 4e. Process Stdout/Stderr Capture

For debugging, redirect subprocess output to files:
```kotlin
.redirectOutput(File(tmpDir, "stdout.log"))
.redirectError(File(tmpDir, "stderr.log"))
```
On failure, the test can read these files and include them in the error message.

### 4f. Timeout

Use `process.waitFor(10, TimeUnit.MINUTES)`. If the process does not complete in time, destroy it and fail the test.

### 4g. The `installDist` Dependency

**Recommended approach**: Add a check at the start of the test that verifies the binary exists. If not, fail with a clear message: "Binary not found. Run `./gradlew :app:installDist` first."

The `test_with_integ.sh` script (if it exists) should include `installDist` before running tests.

## 5. Test Structure (BDD)

```
describe("GIVEN a temp git repo with a hello-world ticket").config(isIntegTestEnabled())
    // Setup: create temp dir, git init, ticket file, workflow config

    describe("WHEN the binary runs with straightforward workflow and --iteration-max 1")
        // Execute: ProcessBuilder, wait for completion

        it("THEN the process exits with code 0")

        it("THEN a git feature branch was created")

        it("THEN hello-world.sh exists in the repo")

        it("THEN .ai_out/ directory structure was created")

    afterSpec
        // Cleanup: kill tmux sessions, delete temp dir
```

## 6. File Layout

```
app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/
    StraightforwardWorkflowE2EIntegTest.kt
```

## 7. Open Questions / Decisions Needed

### 7a. RESOLVED: GLM via environment inheritance
Set GLM env vars on ProcessBuilder. The `claude` CLI in tmux will inherit them.

### 7b. RESOLVED: Not extending SharedContextDescribeSpec
This test runs the binary externally. It does not need internal ShepherdContext wiring. Use `AsgardDescribeSpec` directly.

### 7c. OPEN: Binary build dependency
**Options**:
- A) Add `tasks.test { dependsOn(":app:installDist") }` in `build.gradle.kts`
- B) Check binary exists at test start, fail hard with clear message
- C) Run `installDist` in `beforeSpec`

**Recommendation**: Option B (fail hard with clear message). Option A slows all test runs. Option C adds complexity.
Modify `test_with_integ.sh` to include `installDist`.

### 7d. CONSIDERATION: test_with_integ.sh
Check if `test_with_integ.sh` exists. If yes, add `installDist` to it. If not, create it or document the requirement.

### 7e. CONSIDERATION: Agent output non-determinism
GLM agents may produce slightly different output each run. The test should assert on structural properties (file exists, exit code) rather than exact content.

## 8. Acceptance Criteria

1. Test class compiles and is discoverable by Kotest runner
2. Test is gated by `isIntegTestEnabled()` -- skipped in normal `./test.sh` runs
3. When run with `-PrunIntegTests=true`, the test:
   - Creates a temp git repo
   - Runs the binary subprocess
   - Waits for completion (up to 10 min)
   - Verifies exit code 0
   - Verifies agent-produced output exists
   - Cleans up all resources
4. Test output is debuggable -- subprocess stdout/stderr captured to files
5. No tmux session leaks after test completion

## 9. Risk Mitigation

| Risk | Mitigation |
|------|------------|
| GLM agent fails to produce correct output | Assert exit code 0 as primary check; file existence as secondary |
| Port already in use | Use `ServerSocket(0)` dynamic allocation |
| Binary not built | Fail hard with actionable error message |
| Tmux session leak | Record sessions before/after, kill delta in afterSpec |
| Test runs too long | 10-minute timeout with process.destroy() |
| Working tree not clean | `git add .` + `git commit` everything before running binary |
| Docker check fails | Accepted -- test can only run in Docker (consistent with project design) |

## 10. Implementation Sequence Summary

1. Create test file at `app/src/test/kotlin/com/glassthought/shepherd/integtest/e2e/StraightforwardWorkflowE2EIntegTest.kt`
2. Implement temp dir + git init + ticket fixture setup
3. Copy `config/workflows/straightforward.json` to temp dir
4. Implement environment variable configuration (including GLM)
5. Implement ProcessBuilder invocation with stdout/stderr capture
6. Implement exit code + output verification assertions
7. Implement cleanup (tmux sessions + temp dir)
8. Run with `./gradlew :app:installDist && ./gradlew :app:test -PrunIntegTests=true`
9. Verify test passes end-to-end
