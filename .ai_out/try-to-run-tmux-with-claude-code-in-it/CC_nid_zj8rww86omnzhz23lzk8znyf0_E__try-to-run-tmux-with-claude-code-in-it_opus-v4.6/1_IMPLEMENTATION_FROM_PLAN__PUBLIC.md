# Implementation Summary: Tmux + Claude Code Integration

## What Was Implemented

### New Files

1. **`app/src/main/kotlin/org/example/TmuxSessionManager.kt`**
   - `data class TmuxSession(val sessionName: String)` -- represents a created tmux session.
   - `class TmuxSessionManager(outFactory: OutFactory)` -- manages tmux session lifecycle:
     - `createSession(sessionName, command)` -- creates a detached tmux session via `tmux new-session -d -s`.
     - `killSession(session)` -- kills a session via `tmux kill-session -t`.
     - `sessionExists(sessionName)` -- checks existence via `tmux has-session -t`.
   - Uses `ProcessBuilder` on `Dispatchers.IO`, drains stdout to prevent buffer blocking.
   - Throws `IllegalStateException` on non-zero exit codes.

2. **`app/src/main/kotlin/org/example/TmuxCommunicator.kt`**
   - `class TmuxCommunicator(outFactory: OutFactory)` -- sends keystrokes to tmux sessions:
     - `sendKeys(session, text)` -- sends text literally (`-l` flag) then sends `Enter` separately.
     - `sendRawKeys(session, keys)` -- sends raw keys (for special keys like `C-c`, `Escape`).
   - The `-l` flag ensures words like "Enter", "Space", "Escape" in text are NOT interpreted as tmux key names.

3. **`app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt`** (4 tests)
   - GIVEN createSession with bash THEN session exists.
   - GIVEN existing session THEN sessionExists returns true.
   - GIVEN non-existent name THEN sessionExists returns false.
   - GIVEN existing session WHEN killSession THEN session no longer exists.

4. **`app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt`** (1 test)
   - GIVEN tmux bash session WHEN sendKeys with echo command THEN output file is created with correct content.
   - Uses polling (not `delay`) to wait for file creation.

### Modified Files

5. **`app/src/main/kotlin/org/example/App.kt`**
   - `main()` now creates a detached tmux session running `claude` and sends keystrokes via `TmuxCommunicator`.
   - `InteractiveProcessRunner` class is preserved (import kept) for direct TTY use cases.

## Design Decisions

- **`ValType.STRING_USER_AGNOSTIC`** used for session names (generated timestamps, not user-specific).
- **`ValType.SHELL_COMMAND`** used for commands and keystroke text.
- **`-l` flag on `send-keys`**: text is sent literally so that prompt text containing words like "Enter" or "Escape" is not misinterpreted as tmux key names. The actual Enter keypress is sent as a separate `send-keys` command without `-l`.
- **Polling with `Thread.sleep(100)` in tests**: used instead of `delay()` for file creation synchronization, with a 5-second deadline. This follows the project standard of avoiding `delay()` for synchronization.
- **JUnit `@Test` style** used (matching existing test patterns in this repo), not Kotest `DescribeSpec` (which is used in the thorg-root kotlin-mp project).

## Test Results

All 12 tests pass (5 pre-existing + 5 new TmuxSessionManager + 1 new TmuxCommunicator + 1 AppTest):
- 0 failures
- 0 errors
