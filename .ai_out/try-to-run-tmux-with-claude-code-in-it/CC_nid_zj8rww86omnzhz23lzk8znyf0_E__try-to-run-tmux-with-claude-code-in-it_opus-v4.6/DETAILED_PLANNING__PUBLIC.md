# Detailed Implementation Plan: Tmux + Claude Code Integration

## 1. Problem Understanding

### Goal
Run Claude Code inside a **detached tmux session** managed by Kotlin, enabling programmatic keystroke-based communication with the Claude process. This replaces the current interactive TTY-passthrough approach (`InteractiveProcessRunner`) with a headless, automatable approach suitable for agent orchestration.

### Key Requirements
1. Create a detached tmux session named `agent-harness__<uniqueId>`
2. Print the session name to stdout from Kotlin
3. Launch `claude` inside that tmux session
4. Create a `TmuxCommunicator` class that sends keystrokes to the session
5. Demonstrate end-to-end by sending a prompt to Claude via keystrokes and verifying the output file

### Constraints
- `InteractiveProcessRunner` is preserved as-is (this is a parallel code path)
- Constructor injection, `Out`/`OutFactory` logging, no singletons
- `suspend` functions consistent with coroutine patterns
- Tests use JUnit 5 with GIVEN/WHEN/THEN naming, one assert per `@Test` method
- ProcessBuilder for all tmux CLI interactions (no shell script wrapping)

### Assumptions
- `tmux` is installed on the host machine (fail explicitly if not)
- The target environment is Unix/Linux (tmux does not exist on Windows)
- `claude` CLI is installed and available on `$PATH`
- This is a local development / headless agent scenario, not a CI pipeline

---

## 2. High-Level Architecture

### Component Diagram (Text)

```
main(args)
  |
  +-- App (entry point wiring)
  |     |
  |     +-- [existing] InteractiveProcessRunner  (TTY passthrough -- UNCHANGED)
  |     |
  |     +-- [NEW] TmuxSessionManager             (create/destroy tmux sessions)
  |     |     |
  |     |     +-- uses ProcessBuilder to run: tmux new-session, tmux kill-session, tmux has-session
  |     |
  |     +-- [NEW] TmuxCommunicator               (send keystrokes to a tmux session)
  |           |
  |           +-- uses ProcessBuilder to run: tmux send-keys
  |
  +-- run.sh                                     (modified to forward args to app binary)
```

### Data Flow

1. `App.main(args)` checks for `--tmux` flag
2. Creates a `TmuxSessionManager` and calls `createSession("claude")`
3. `TmuxSessionManager` runs `tmux new-session -d -s <sessionName> claude` via ProcessBuilder
4. Session name is printed to stdout: `println("tmux_session=[${sessionName}]")`
5. A `TmuxCommunicator` is created for that session name
6. `TmuxCommunicator.sendKeys(text)` runs `tmux send-keys -t <sessionName> "<text>" Enter`
7. The caller can later call `TmuxSessionManager.killSession(sessionName)` for cleanup

### Key Interfaces and Data Types

```
data class TmuxSessionName(val value: String)
  companion object:
    fun generate(): TmuxSessionName  // produces "agent-harness__<timestampMillis>"

data class TmuxSessionResult(
    val sessionName: TmuxSessionName,
    val exitCode: Int,
)

class TmuxSessionManager(outFactory: OutFactory)
  suspend fun createSession(command: String): TmuxSessionResult
  suspend fun killSession(sessionName: TmuxSessionName): Int
  suspend fun hasSession(sessionName: TmuxSessionName): Boolean

class TmuxCommunicator(
    outFactory: OutFactory,
    private val sessionName: TmuxSessionName,
)
  suspend fun sendKeys(text: String): Int
  suspend fun sendKeysWithoutEnter(text: String): Int
```

---

## 3. Implementation Phases

### Phase 1: TmuxSessionName data class and session name generation

**Goal:** Define the typed session name and a deterministic naming scheme.

**Components Affected:**
- NEW: `app/src/main/kotlin/org/example/tmux/TmuxSessionName.kt`

**Key Steps:**
1. Create package `org.example.tmux`
2. Create `TmuxSessionName` data class with a single `value: String` property
3. Create a companion factory method `fun generate(): TmuxSessionName` that produces `agent-harness__<timestamp_millis>` (using `System.currentTimeMillis()`)
   - This is simple and unique enough for single-machine usage
   - No UUID needed -- timestamp is sufficient for 80% value (Pareto)

**Dependencies:** None

**Verification:** Unit test confirms the generated name matches the pattern `agent-harness__\d+`

---

### Phase 2: TmuxSessionManager

**Goal:** Create, query, and destroy tmux sessions via the tmux CLI.

**Components Affected:**
- NEW: `app/src/main/kotlin/org/example/tmux/TmuxSessionManager.kt`
- NEW: `app/src/main/kotlin/org/example/tmux/TmuxSessionResult.kt`

**Key Steps:**
1. Create `TmuxSessionResult` data class: `(sessionName: TmuxSessionName, exitCode: Int)`
2. Create `TmuxSessionManager(outFactory: OutFactory)` class
3. Implement `suspend fun createSession(command: String): TmuxSessionResult`:
   - Generate a `TmuxSessionName`
   - Build command: `tmux new-session -d -s <sessionName> <command>`
     - `-d`: detached (headless)
     - `-s`: session name
     - The trailing `command` is the command to run inside the session (e.g., `claude`)
   - Execute via ProcessBuilder, capture exit code
   - Log the session name using `Out.info("tmux_session_created", Val(sessionName.value, ValType.STRING_USER_AGNOSTIC))`
   - Return `TmuxSessionResult`
4. Implement `suspend fun hasSession(sessionName: TmuxSessionName): Boolean`:
   - Run `tmux has-session -t <sessionName>`
   - Return `true` if exit code is 0, `false` otherwise
5. Implement `suspend fun killSession(sessionName: TmuxSessionName): Int`:
   - Run `tmux kill-session -t <sessionName>`
   - Return exit code
6. **Error handling:**
   - If `tmux` binary is not found, ProcessBuilder will throw `IOException` -- let it bubble up (do NOT log and throw)
   - If session name already exists, `tmux new-session` returns non-zero exit code -- captured in `TmuxSessionResult.exitCode`

**Tmux Commands Reference:**

| Operation | Command |
|-----------|---------|
| Create detached session | `tmux new-session -d -s <name> [command]` |
| Check session exists | `tmux has-session -t <name>` |
| Kill session | `tmux kill-session -t <name>` |
| List sessions | `tmux list-sessions` |

**Dependencies:** Phase 1

**Verification:**
- Unit test: construction compiles
- Integration test (requires tmux): create session with `echo hello`, verify `hasSession` returns true, kill session, verify `hasSession` returns false

---

### Phase 3: TmuxCommunicator

**Goal:** Send keystrokes to a running tmux session.

**Components Affected:**
- NEW: `app/src/main/kotlin/org/example/tmux/TmuxCommunicator.kt`

**Key Steps:**
1. Create `TmuxCommunicator(outFactory: OutFactory, private val sessionName: TmuxSessionName)`
2. Implement `suspend fun sendKeys(text: String): Int`:
   - Run `tmux send-keys -t <sessionName> <text> Enter`
   - The `Enter` literal at the end tells tmux to simulate pressing Enter after the text
   - Log via `Out.info("tmux_send_keys", Val(text, ValType.SHELL_COMMAND))`
   - Return exit code
3. Implement `suspend fun sendKeysWithoutEnter(text: String): Int`:
   - Same as above but without the `Enter` argument
   - Useful for partial input or special key sequences

**Tmux send-keys details:**
- Command: `tmux send-keys -t <sessionName> "<text>" Enter`
- The text is NOT interpreted as a shell command -- tmux literally types it character-by-character into the target pane
- `Enter` is a special tmux key name that simulates pressing the Enter key
- Important: the text and `Enter` must be **separate arguments** to `send-keys`, not concatenated

**Dependencies:** Phase 1

**Verification:**
- Integration test (requires tmux): create a session running `cat` (or `bash`), send keys "hello", capture pane content with `tmux capture-pane`, verify the text was sent

---

### Phase 4: Wire into App.kt main() and run.sh

**Goal:** Add a tmux-based code path in `main()` and update `run.sh` to support it.

**Components Affected:**
- MODIFIED: `app/src/main/kotlin/org/example/App.kt`
- MODIFIED: `run.sh`

**Key Steps:**

1. **App.kt changes:**
   - Change `fun main()` to `fun main(args: Array<String>)` to accept CLI arguments
   - Add a second code path alongside the existing `InteractiveProcessRunner` flow
   - Use a simple CLI argument check: if `args` contains `--tmux`, use the tmux path; otherwise, use interactive path
   - The tmux path:
     1. Create `TmuxSessionManager(outFactory)`
     2. Call `createSession("claude")` to get a `TmuxSessionResult`
     3. `println("tmux_session=[${result.sessionName.value}]")` -- bracket-delimited per bash logging convention
     4. Create `TmuxCommunicator(outFactory, result.sessionName)`
     5. Send the test prompt: `communicator.sendKeys("Write 'hello world from tmux' to /tmp/out")`
     6. Print completion message
   - The existing interactive path remains the default (no `--tmux` flag)

2. **run.sh changes:**
   - Forward all CLI arguments (`"${@}"`) to the app binary
   - Usage: `./run.sh --tmux` for tmux mode, `./run.sh` for interactive mode

**Dependencies:** Phases 1-3

**Verification:**
- Manual verification: run `./run.sh --tmux`, observe session created, keystrokes sent, `/tmp/out` file created by Claude
- Existing `./run.sh` (no args) continues to work with interactive mode

---

### Phase 5: Tests

**Goal:** Comprehensive test coverage for all new classes.

**Components Affected:**
- NEW: `app/src/test/kotlin/org/example/tmux/TmuxSessionNameTest.kt`
- NEW: `app/src/test/kotlin/org/example/tmux/TmuxSessionManagerTest.kt`
- NEW: `app/src/test/kotlin/org/example/tmux/TmuxCommunicatorTest.kt`

**Key Steps:**

#### 5a. TmuxSessionNameTest (unit -- always runs)
1. `GIVEN TmuxSessionName.generate WHEN called THEN name starts with agent-harness__`
2. `GIVEN TmuxSessionName.generate WHEN called THEN name matches pattern agent-harness__\d+`
3. `GIVEN TmuxSessionName.generate WHEN called twice THEN names are different`
4. `GIVEN TmuxSessionName WHEN constructed with a value THEN value is preserved`

#### 5b. TmuxSessionManagerTest (integration -- requires tmux)
Tests that require tmux should be in a single test class. If `tmux` is not available on the machine, the **entire test class** should be disabled (not individual tests). Use a companion object / `@BeforeAll` with JUnit 5 `Assumptions.assumeTrue()` to check for tmux and skip the class.

1. `GIVEN tmux is available WHEN createSession is called with echo THEN exit code is 0`
2. `GIVEN a created session WHEN hasSession is called THEN it returns true`
3. `GIVEN a created session WHEN killSession is called THEN exit code is 0`
4. `GIVEN a killed session WHEN hasSession is called THEN it returns false`
5. `GIVEN a non-existent session WHEN hasSession is called THEN it returns false`

Each test should create its own session and clean up after itself (kill the session in `@AfterEach`).

#### 5c. TmuxCommunicatorTest (integration -- requires tmux)
Same class-level gating as TmuxSessionManagerTest.

1. `GIVEN a tmux session running bash WHEN sendKeys is called with echo hello THEN the text appears in the pane`
   - Use `tmux capture-pane -t <session> -p` to read pane content and assert
2. `GIVEN a tmux session WHEN sendKeys is called THEN exit code is 0`

**Dependencies:** Phases 1-3

**Verification:** `./gradlew :app:test` passes (integration tests enabled when tmux is present)

---

## 4. Technical Considerations

### ProcessBuilder Usage Pattern
All tmux commands follow the same execution pattern. Extract a private helper method to avoid duplication:

```kotlin
// Pseudocode for the common execution pattern
private suspend fun executeTmuxCommand(vararg args: String): Int = withContext(Dispatchers.IO) {
    val process = ProcessBuilder("tmux", *args)
        .redirectErrorStream(true)
        .start()
    // MUST drain stdout to avoid process hanging on full output buffer
    process.inputStream.bufferedReader().readText()
    process.waitFor()
}
```

**Critical detail:** The stdout/stderr of the ProcessBuilder must be consumed (or redirected) to prevent the process from blocking when the output buffer fills up. Options:
- `redirectErrorStream(true)` + drain `inputStream`
- Redirect to `/dev/null` via `processBuilder.redirectOutput(File("/dev/null"))`

The `TmuxSessionManager` and `TmuxCommunicator` both need this pattern. Two options for sharing:
- **Option A (recommended):** Both classes have their own private helper. Duplication is minimal (3-5 lines) and keeps classes self-contained.
- **Option B:** Extract a shared `TmuxCommandExecutor` utility class. Only worth it if more tmux-related classes are planned.

Go with Option A for now (KISS, Pareto).

### Timing / Synchronization
- After `createSession("claude")`, Claude takes time to start up. The test prompt (`sendKeys`) should not be sent immediately.
- For the manual end-to-end test in Phase 4, a simple `delay` in the demo flow is acceptable (this is NOT a test, it is a one-time demo path).
- For automated tests in Phase 5, use `bash` or `cat` instead of `claude` to avoid startup timing issues and external dependency on the `claude` binary.
- For `bash` sessions, a short poll loop checking for expected output (via `tmux capture-pane`) is preferable to a blind delay.

### Error Handling Strategy

| Error | How Handled |
|-------|------------|
| `tmux` not installed | `IOException` from ProcessBuilder bubbles up. No catch, no log-and-throw. |
| Session name collision | Non-zero exit code in `TmuxSessionResult.exitCode`. Caller decides. |
| `tmux send-keys` to dead session | Non-zero exit code returned from `sendKeys()`. |
| `claude` not installed | tmux session is created but claude fails inside it. Session still exists. Caller should check. |

### ValType Considerations
The project uses `ValType` from `asgardCore` for structured logging. Relevant existing types:
- `ValType.SHELL_COMMAND` -- for the tmux command being executed
- `ValType.STRING_USER_AGNOSTIC` -- for session names (machine-generated, not user-specific)

No new `ValType` entries are needed. If in the future a `TMUX_SESSION_NAME` type is desired, it can be added to the asgardCore `ValType` enum, but that touches the submodule (out of scope).

---

## 5. File Summary

### New Files

| File | Responsibility |
|------|---------------|
| `app/src/main/kotlin/org/example/tmux/TmuxSessionName.kt` | Typed wrapper for tmux session name + `generate()` factory |
| `app/src/main/kotlin/org/example/tmux/TmuxSessionResult.kt` | Result data class from session creation |
| `app/src/main/kotlin/org/example/tmux/TmuxSessionManager.kt` | Create, query, destroy tmux sessions |
| `app/src/main/kotlin/org/example/tmux/TmuxCommunicator.kt` | Send keystrokes to a tmux session |
| `app/src/test/kotlin/org/example/tmux/TmuxSessionNameTest.kt` | Unit tests for session name generation |
| `app/src/test/kotlin/org/example/tmux/TmuxSessionManagerTest.kt` | Integration tests for session lifecycle |
| `app/src/test/kotlin/org/example/tmux/TmuxCommunicatorTest.kt` | Integration tests for keystroke sending |

### Modified Files

| File | Change |
|------|--------|
| `app/src/main/kotlin/org/example/App.kt` | Add `--tmux` code path, accept `args` in `main()` |
| `run.sh` | Forward `"${@}"` args to app binary |

---

## 6. Testing Strategy

### Unit Tests (always run)
- `TmuxSessionNameTest` -- validates naming pattern and uniqueness

### Integration Tests (require tmux on host)
- `TmuxSessionManagerTest` -- full session lifecycle (create, has, kill)
- `TmuxCommunicatorTest` -- keystroke delivery verification using `tmux capture-pane`
- **Class-level gating:** If tmux is not available, the entire test class is skipped via JUnit 5 `Assumptions.assumeTrue()` in `@BeforeAll`. Individual tests are NEVER conditionally skipped.
- **Cleanup:** Each test method cleans up its session in `@AfterEach` to avoid leaking tmux sessions.

### Manual End-to-End Test
- Run `./run.sh --tmux`
- Verify `/tmp/out` contains "hello world from tmux" (written by Claude)
- Verify `tmux list-sessions` shows the `agent-harness__*` session

### Edge Cases to Test
- Session name uniqueness across rapid successive calls
- Sending keys to a session that no longer exists (non-zero exit code)
- Creating a session when tmux is not installed (IOException)
- Killing a session that does not exist (non-zero exit code, no exception)

---

## 7. Open Questions / Decisions Needed

None -- all requirements are clear from the ticket and clarification documents. The plan follows the simplest viable path (Pareto 80/20).

---

## 8. Implementation Order Summary

```
Phase 1: TmuxSessionName data class
  |
  +-- Phase 2: TmuxSessionManager       (depends on Phase 1)
  |
  +-- Phase 3: TmuxCommunicator         (depends on Phase 1)
  |
  +-- Phase 5a: TmuxSessionNameTest     (depends on Phase 1)
  |
  +-- Phase 5b: TmuxSessionManagerTest  (depends on Phase 2)
  |
  +-- Phase 5c: TmuxCommunicatorTest    (depends on Phase 3)
  |
  Phase 4: App.kt + run.sh wiring       (depends on Phases 2, 3)
```

Phases 2 and 3 are independent of each other and can be implemented in parallel.
