# Implementation Review: Tmux + Claude Code Integration

## Summary

The implementation introduces two new classes (`TmuxSessionManager` and `TmuxCommunicator`) and updates `App.kt` to create a tmux session running `claude` and send keystrokes to it. All five ticket requirements are met. Tests pass. Existing tests (`InteractiveProcessRunnerTest`, `AppTest`) are untouched. The code follows project standards for constructor injection, `Out` logging with structured values, and coroutine usage. The `-l` flag usage in `TmuxCommunicator.sendKeys` is correct -- literal text is sent first, then a non-literal `Enter` key press, which prevents words like "Space" or "Escape" in user text from being misinterpreted as tmux key names.

**Overall assessment**: Solid first implementation. Two IMPORTANT issues to address, both straightforward.

## No CRITICAL Issues

No security, correctness, or data loss issues found.

## IMPORTANT Issues

### 1. DRY Violation: Duplicated `runTmuxCommand` method

`TmuxSessionManager.runTmuxCommand` (lines 99-108) and `TmuxCommunicator.runTmuxCommand` (lines 79-88) are identical:

```kotlin
// In TmuxSessionManager.kt, line 99:
private suspend fun runTmuxCommand(vararg args: String): Int {
    return withContext(Dispatchers.IO) {
        val process = ProcessBuilder("tmux", *args)
            .redirectErrorStream(true)
            .start()
        process.inputStream.readBytes()
        process.waitFor()
    }
}

// In TmuxCommunicator.kt, line 79:
private suspend fun runTmuxCommand(vararg args: String): Int {
    return withContext(Dispatchers.IO) {
        val process = ProcessBuilder("tmux", *args)
            .redirectErrorStream(true)
            .start()
        process.inputStream.readBytes()
        process.waitFor()
    }
}
```

**Suggested fix**: Extract a `TmuxCommandRunner` class (or similar) that both `TmuxSessionManager` and `TmuxCommunicator` receive via constructor injection. This also follows DIP -- if you ever need to mock tmux commands in unit tests, you inject a test double of the runner instead of shelling out to real tmux.

```kotlin
class TmuxCommandRunner {
    suspend fun run(vararg args: String): Int {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder("tmux", *args)
                .redirectErrorStream(true)
                .start()
            process.inputStream.readBytes()
            process.waitFor()
        }
    }
}
```

Then inject it:
```kotlin
class TmuxSessionManager(
    outFactory: OutFactory,
    private val tmuxCommandRunner: TmuxCommandRunner,
)
```

### 2. App.kt: No session cleanup -- tmux session leaks on every run

`App.kt` creates a tmux session and sends keys, but never calls `killSession`. The session persists after the JVM exits, and each run creates a new one. Over time, this accumulates orphaned tmux sessions.

```kotlin
// App.kt lines 36-39 -- session is created and used, but never killed
val session = sessionManager.createSession(sessionName, "claude")
communicator.sendKeys(session, "Write 'hello world from tmux' to /tmp/out")
// Missing: sessionManager.killSession(session)
```

**#QUESTION_FOR_HUMAN**: Is the intent that the tmux session should persist after `main()` exits (so the user can attach to it later and see claude working)? If so, this is by design and should be documented with a comment in `App.kt`. If not, a `try/finally` with `killSession` is needed. Given the ticket says "Run claude in that tmux session" and "send keystrokes", it seems like the session should stay alive so claude can process the request -- but this should be explicitly documented.

## Suggestions

### 1. Tests use JUnit 5 rather than Kotest DescribeSpec

The `CLAUDE.md` testing standards specify BDD with Kotest `DescribeSpec` and `AsgardDescribeSpec`. However, Kotest is not in the project's Gradle dependencies, and all existing tests (`InteractiveProcessRunnerTest`, `AppTest`) use JUnit 5. The new tests follow the existing JUnit pattern, which is the right call for consistency. But this is a mismatch with the documented standard.

**#QUESTION_FOR_HUMAN**: Should Kotest be added to the project to align with the documented standard, or should the CLAUDE.md testing standards be updated to reflect that this project uses JUnit 5?

### 2. TmuxCommunicatorTest: single test covers two behaviors

The test `GIVEN a tmux session running bash WHEN sendKeys with echo command THEN file is created with expected content` (`TmuxCommunicatorTest.kt`, line 41) is the only test for `TmuxCommunicator`. Per the "one assert per test" standard, this is fine (it has one assertion). But `TmuxCommunicator` has two public methods (`sendKeys` and `sendRawKeys`) and only `sendKeys` is tested. Consider adding a test for `sendRawKeys`.

### 3. Minor: `TmuxCommunicatorTest` uses `Thread.sleep` for polling

The test uses `Thread.sleep(100)` in a polling loop (line 58). While the CLAUDE.md says "Do NOT use `delay` for synchronization", and this is `Thread.sleep` not `delay`, the polling pattern itself is reasonable given it's waiting for a file-system side-effect from an external process. The timeout of 5 seconds is adequate. No action needed, just noting.

### 4. Consider capturing tmux error output for diagnostics

`runTmuxCommand` currently drains stdout/stderr (merged via `redirectErrorStream(true)`) and discards it. When tmux fails, the error message is lost, and the caller only sees the exit code in the exception. Capturing stderr/stdout and including it in the `IllegalStateException` message would aid debugging:

```kotlin
val output = process.inputStream.readBytes().decodeToString()
val exitCode = process.waitFor()
// ... then include `output` in error messages
```

This is low priority for now but would be valuable as the tmux integration matures.

## Documentation Updates Needed

None required. The code is well-documented with KDoc, and the `CLAUDE.md` does not need updates for this change.

## Verdict

**Approve with changes** -- address the two IMPORTANT items (DRY violation on `runTmuxCommand`, and clarify/document the session lifecycle intent in `App.kt`). The suggestions are optional.
