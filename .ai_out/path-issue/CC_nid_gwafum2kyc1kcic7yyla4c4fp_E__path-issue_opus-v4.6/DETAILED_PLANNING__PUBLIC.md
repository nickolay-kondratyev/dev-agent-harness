# Detailed Plan: Integration Test for PATH in tmux Sessions

## 1. Problem Understanding

### Goal
Create a **focused integration test** that verifies the `PATH` environment variable inside a tmux session contains the callback scripts directory, using the same command-building infrastructure that production code uses (`ClaudeCodeAdapter.buildStartCommand()`).

### What exists today
- **Unit test** (`ClaudeCodeAdapterTest`): Verifies the command string contains `export PATH=$PATH:<dir>` -- a string-level check.
- **Unit test** (`CallbackScriptsDirTest`): Verifies the `CallbackScriptsDir` type validates directory/script/executable.
- **Heavy integ test** (`AgentFacadeImplIntegTest`): Spawns a full GLM-backed agent. Tests the signal flow, not PATH specifically.

### What is missing
No test verifies that the PATH is **actually set** inside the tmux session at runtime. The command string could be correct, but shell quoting, tmux escaping, or environment inheritance could cause the PATH to be wrong once bash actually evaluates it.

### Constraints
- Requires tmux (gated with `isIntegTestEnabled()`)
- Must NOT require Claude, GLM, or any LLM
- Must clean up tmux sessions after test
- Must follow existing BDD / Kotest DescribeSpec patterns

---

## 2. Key Design Decision: Test Approach

### Recommended: Option A -- Replace `claude` with `echo $PATH > file`

**Approach**: Use `ClaudeCodeAdapter.buildStartCommand()` to generate the real command, then do a targeted string replacement of the `claude ...` substring with a command that writes `$PATH` to a temp file. Start this modified command in a tmux session via `TmuxSessionManager`, poll for the temp file, and assert the PATH contains the callback scripts directory.

**Why this is the right approach**:
1. It tests the **real command-building pipeline** (`ClaudeCodeAdapter.buildStartCommand()`) -- not a hand-crafted command. If the adapter changes how it exports PATH, this test exercises the actual output.
2. It tests the **real tmux session creation** (`TmuxSessionManager.createSession()`) -- ensuring the `bash -c '...'` wrapper and escaping work correctly in a real tmux environment.
3. The only synthetic part is replacing `claude <flags> <message>` with `echo $PATH > file` -- minimal deviation from the real flow.
4. No LLM, no HTTP server, no sessions state needed.

**Why NOT Option B** (start tmux, send-keys to echo PATH): This would test tmux's `send-keys` mechanism, not the PATH that was set by the `bash -c '...'` command at session start. It would test a different code path.

**Why NOT Option C** (modify the adapter): Violates the principle of testing production code as-is. We should not add test hooks to `ClaudeCodeAdapter`.

---

## 3. High-Level Architecture

```
Test Flow:

    1. Resolve CallbackScriptsDir (real, validated -- via IntegTestHelpers)
    2. Create ClaudeCodeAdapter with that dir
    3. Call adapter.buildStartCommand(...) with dummy params
    4. String-replace "claude ..." portion with "echo $PATH > /tmp/path-output-file"
    5. sessionManager.createSession(name, modifiedCommand)
    6. Poll for output file (AsgardAwaitility)
    7. Read file, assert it contains callbackScriptsDir.path
    8. Cleanup: kill tmux session, delete temp file
```

### Components touched
- `ClaudeCodeAdapter` -- used as-is (read-only)
- `TmuxSessionManager` -- used as-is (read-only)
- `IntegTestHelpers` -- used as-is for `resolveCallbackScriptsDir()`
- **New test class** -- the only new code

---

## 4. Implementation Phases

### Phase 1: Create the test class

**Goal**: A single, focused integration test file.

**File location**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/TmuxPathIntegTest.kt`

**Why this location**: The `integtest` package contains all integration tests that use `SharedContextDescribeSpec` and `shepherdContext`. The `com.glassthought.bucket` package is for lower-level infrastructure tests. This test exercises the adapter + tmux interaction, which is application-level integration.

**What to extend**: `SharedContextDescribeSpec`. This provides `shepherdContext.infra.tmux.sessionManager` (needed) and `shepherdContext.infra.outFactory` (needed for `ClaudeCodeAdapter`). Even though we create a separate `ClaudeCodeAdapter` instance (with the real `CallbackScriptsDir`, not the sentinel), we still need tmux infrastructure from the shared context.

**Key steps**:

1. Create test class extending `SharedContextDescribeSpec`
2. Annotate with `@OptIn(ExperimentalKotest::class)`
3. Gate top-level describe with `.config(isIntegTestEnabled())`
4. In the describe block body (NOT suspend context):
   - Get `sessionManager` from `shepherdContext.infra.tmux.sessionManager`
   - Resolve `CallbackScriptsDir` via `IntegTestHelpers.resolveCallbackScriptsDir()`
   - Create a `ClaudeCodeAdapter` instance with the resolved scripts dir and a dummy server port
   - Build `BuildStartCommandParams` with dummy values (any model, any tools, any bootstrap message -- we only care about the PATH export portion)
   - Call `adapter.buildStartCommand(params)` to get the full `TmuxStartCommand`
5. Create a helper to replace the `claude ...` portion of the command with `echo $PATH > <tmpFile>`
6. Track created tmux sessions and temp files for cleanup in `afterEach`

### Phase 2: Implement command replacement

**Goal**: Reliably replace the `claude ...` portion of the generated command with an `echo $PATH` command.

**Strategy**: The command structure from `ClaudeCodeAdapter` is:
```
bash -c 'cd <workdir> && unset CLAUDECODE && export GUID=... && export PORT=... && export PATH=$PATH:<dir> && claude --model ... --tools ... --dangerously-skip-permissions "bootstrap message..."'
```

The `claude` command starts after the last `&& ` before `claude ` and extends to the end of the `bash -c '...'` wrapper (minus the trailing `'`).

**Recommended replacement approach**: Find the index of `&& claude ` in the inner command (after the `bash -c '` prefix). Replace everything from `claude ` to the closing `'` with the echo command.

More precisely:
- The inner command (inside `bash -c '...'`) ends with `'`
- Locate `&& claude ` in the full command string
- Replace from `claude ...` (everything after that `&& `) up to the closing `'` with:
  `echo \\$PATH > <tmpFilePath>'`

Note the `\\$PATH` -- inside the `bash -c '...'` single-quoted wrapper, `$PATH` must remain as a literal `$PATH` for bash to expand at evaluation time. Since the adapter already escapes `$PATH` in the export line as `\$PATH`, the same pattern should work. However, since we are inside single quotes (`bash -c '...'`), the `$` is already literal. So the replacement should simply use `$PATH`.

Actually, let me reconsider. The full command is:
```
bash -c '...export PATH=$PATH:/some/dir && claude ...'
```

Inside single quotes, `$PATH` is literal -- bash does NOT expand it. But wait, the adapter code builds:
```kotlin
"export PATH=\$PATH:${callbackScriptsDir.path}"
```

And `escapeForBashC` escapes single quotes. The `\$PATH` in Kotlin becomes `$PATH` in the string, and since it is inside `bash -c '...'`, the `$` is passed literally to the inner bash, which then expands it.

So inside the `bash -c '...'` wrapper, `$PATH` is already literal. Our replacement should just be:
```
echo $PATH > /path/to/output/file
```

placed where `claude ...` was.

**Concrete approach**: Use regex or string operations to find `claude ` after the last `&& ` before the closing single quote, and replace everything from there to the end of the inner command.

A simpler, more robust approach: The `claude` command always starts with `claude --model`. Find `claude --model` in the string and replace from there to the closing `'` (exclusive) with the echo command.

### Phase 3: Implement assertions

**Goal**: Read the output file and verify PATH contents.

**Key steps**:

1. After starting the tmux session with the modified command, use `AsgardAwaitility` to poll for the output file (same pattern as `TmuxCommunicatorIntegTest`).
2. Read the file content as a string.
3. Assert the PATH string contains `callbackScriptsDir.path`.
4. Optionally, also assert the script `callback_shepherd.signal.sh` is actually findable by checking if the full path `<callbackScriptsDir.path>/callback_shepherd.signal.sh` exists (this is already validated by `CallbackScriptsDir.validated()`, but asserting the PATH entry makes the test focused).

**Separate `it` blocks** (one assert per test):
- `it("THEN PATH contains the callback scripts directory")` -- the core assertion
- `it("THEN PATH is non-empty")` -- sanity check that we actually captured something

### Phase 4: Cleanup

**Goal**: Ensure tmux sessions and temp files are cleaned up.

**Pattern**: Follow `TmuxCommunicatorIntegTest` exactly:
- `createdSessions` list in the describe block
- `createdFiles` list in the describe block
- `afterEach` kills sessions and deletes files, with try/catch for already-dead sessions

---

## 5. Technical Considerations

### Working directory for the tmux command
The `BuildStartCommandParams.workingDir` must be a real, existing directory (the `cd` command will fail otherwise). Use `System.getProperty("user.dir")` (the project's `app/` directory when run from Gradle).

### Temp file for PATH output
Write to `.tmp/` directory per project convention: `File(System.getProperty("user.dir"), ".tmp/path-integ-test-output-${System.currentTimeMillis()}")`.

### Timing
The tmux session runs `bash -c '...'` which executes sequentially. The `echo $PATH > file` command runs immediately and the session exits. Polling with `AsgardAwaitility.wait().atMost(5.seconds)` is more than sufficient.

### HandshakeGuid and server port
These are irrelevant to the PATH test but required by `BuildStartCommandParams`. Use dummy values: `HandshakeGuid("path-test-guid")`, server port `99999`.

### The `unset CLAUDECODE` in the command
This is benign -- it just unsets an env var. Does not affect the test.

### GuidScanner
The `ClaudeCodeAdapter` constructor requires a `GuidScanner`. Since we never call `resolveSessionId`, use a no-op fake: `GuidScanner { emptyList() }` (same pattern as `ClaudeCodeAdapterTest`).

---

## 6. Test Structure (BDD)

```
describe("GIVEN a tmux command built by ClaudeCodeAdapter with real CallbackScriptsDir")
    .config(isIntegTestEnabled())

    describe("WHEN the command is executed in a tmux session with claude replaced by echo PATH")

        it("THEN the PATH inside tmux contains the callback scripts directory")

        it("THEN the callback_shepherd.signal.sh script is findable via the PATH")
```

The second assertion is slightly different -- it verifies not just that the directory is on PATH, but that the script within it is reachable. This could be done by replacing `claude` with `which callback_shepherd.signal.sh > <file>` or `command -v callback_shepherd.signal.sh > <file>`.

**Recommendation**: Keep it simple with one primary assertion on PATH content. The `CallbackScriptsDir.validated()` already guarantees the script exists and is executable. The test's job is to verify the PATH export reaches the tmux bash process.

---

## 7. Testing Strategy

### What this test covers
- `ClaudeCodeAdapter.buildStartCommand()` generates a command where `export PATH=$PATH:<dir>` survives:
  - Kotlin string construction
  - `escapeForBashC()` escaping
  - `bash -c '...'` wrapper evaluation
  - tmux session creation

### What this test does NOT cover (and should not)
- That `claude` itself can find the script (that requires a real agent)
- That the script works correctly when called (separate test)
- That the script is on the classpath at build time (compile-time / ContextInitializer test)

### Edge cases
- None critical for this focused test. The callback scripts dir is validated at construction time, so invalid dirs are already caught.

---

## 8. Acceptance Criteria

1. **AC-1**: A new integration test class `TmuxPathIntegTest` exists at `app/src/test/kotlin/com/glassthought/shepherd/integtest/TmuxPathIntegTest.kt`.

2. **AC-2**: The test is gated with `isIntegTestEnabled()` and requires only tmux (no LLM, no GLM, no HTTP server).

3. **AC-3**: The test uses `ClaudeCodeAdapter.buildStartCommand()` to generate the command (not a hand-crafted command string).

4. **AC-4**: The test starts a real tmux session via `TmuxSessionManager.createSession()`.

5. **AC-5**: The test verifies that `$PATH` inside the tmux session contains the callback scripts directory path.

6. **AC-6**: tmux sessions are cleaned up after each test (via `afterEach`).

7. **AC-7**: Temporary output files are cleaned up after each test.

8. **AC-8**: The test passes when run with `./gradlew :app:test -PrunIntegTests=true`.

9. **AC-9**: The test follows BDD style (GIVEN/WHEN/THEN) with one assert per `it` block.

10. **AC-10**: All existing tests continue to pass (`./gradlew :app:test`).

---

## 9. Open Questions / Decisions

None -- the requirements and approach are clear. The test is straightforward and follows well-established patterns from `TmuxCommunicatorIntegTest` and `TmuxSessionManagerIntegTest`.
