# Plan Review: Tmux + Claude Code Integration

## Executive Summary

The plan is solid and well-structured. Two focused classes (`TmuxSessionManager`, `TmuxCommunicator`) with clear SRP is the right decomposition. The tmux command choices are correct. There are a few concerns around output capture for error reporting, shell escaping of `send-keys` arguments, resource cleanup, and test framework alignment. None are blockers -- all are addressable with minor refinements.

**Recommendation: APPROVED WITH MINOR REVISIONS.** Plan iteration can be skipped; the implementer can incorporate these inline.

## Critical Issues (BLOCKERS)

None.

## Major Concerns

### 1. Shell Escaping / Quoting in `sendKeys`

- **Concern**: `tmux send-keys -t <sessionName> <text> Enter` is passed via `ProcessBuilder` which does NOT go through a shell. This means the `text` argument is passed as a single raw argument to tmux -- which is correct. However, tmux's `send-keys` interprets certain strings as key names (e.g., `Space`, `Escape`, `C-c`). If the user's text contains these words, it will be interpreted as key presses rather than literal text.
- **Why**: If the prompt text sent to claude contains words like "Space" or "Enter" as literal substrings in positions tmux interprets, the keystroke delivery will silently corrupt. This is a subtle correctness issue.
- **Suggestion**: Use `tmux send-keys -t <sessionName> -l <text>` with the `-l` (literal) flag for `sendKeys`, and then send `Enter` as a separate `send-keys` call (or append `Enter` without `-l`). The `sendRawKeys` method should remain without `-l` since it intentionally sends key names.

### 2. Output Capture for Error Diagnostics

- **Concern**: The `runTmuxCommand` helper in the plan uses `redirectErrorStream(true)` and `process.waitFor()` but never reads the process output. If tmux fails (e.g., not installed, session name collision), the error message is lost. The plan says "throw descriptive exception" but does not capture stdout/stderr to include in that exception.
- **Why**: Without the tmux error text, debugging becomes guesswork. "tmux command failed with exit code 1" is not actionable.
- **Suggestion**: Read process stdout/stderr into a string before calling `waitFor()`, and include it in the exception message. Example pattern:
  ```kotlin
  val output = process.inputStream.bufferedReader().readText()
  val exitCode = process.waitFor()
  if (exitCode != 0) throw TmuxCommandException("tmux ${args.toList()} failed: exitCode=[$exitCode], output=[$output]")
  ```

### 3. Resource Cleanup Pattern

- **Concern**: The plan mentions `AsgardCloseable.use{}` as optional for `TmuxSession`. Given the ticket's goal of creating a session, sending keystrokes, and verifying output, the session MUST be cleaned up. Without a defined cleanup mechanism, session leaks are likely during development and testing.
- **Why**: Leaked tmux sessions accumulate and can interfere with subsequent runs (especially with timestamp-based names that collide across fast restarts).
- **Suggestion**: Make `TmuxSession` (or a wrapper) implement the cleanup pattern. At minimum, the plan should make cleanup non-optional and specify that tests ALWAYS kill sessions in a `finally` block or equivalent. The `App.kt` code should also clean up.

## Simplification Opportunities (PARETO)

### 1. Consider Merging `TmuxSessionManager` + `TmuxCommunicator` into One Class

- **Current**: Two classes: `TmuxSessionManager` (lifecycle) and `TmuxCommunicator` (I/O).
- **Alternative**: A single `TmuxController` class that handles both lifecycle and keystroke sending. At this stage, there is no scenario where you manage a session without communicating with it or vice versa. The two classes always travel together.
- **Value**: Fewer files, simpler wiring, reduced cognitive overhead. If/when the responsibilities diverge enough to justify splitting, do it then.
- **Counterpoint**: The current two-class split is not bad. It follows SRP and the overhead is minimal. This is a soft suggestion, not a strong recommendation. Either approach is fine.

### 2. Session Name Generation

- **Current**: `agent-harness__${System.currentTimeMillis()}` generated in `App.kt`.
- **Suggestion**: This is fine for now, but consider using a UUID or adding PID to avoid collisions when multiple instances start within the same millisecond. Low priority -- timestamp is adequate for single-instance manual testing.

## Minor Suggestions

### 1. Test Framework Alignment

The plan says "Tests for session creation, existence check, and cleanup" but does not specify the test framework. The existing project uses **JUnit 5 with kotlin-test** (NOT Kotest `DescribeSpec`). While CLAUDE.md testing standards mention Kotest, the project has not adopted it. The new tests should match the existing pattern (JUnit 5 with backtick-named functions and GIVEN/WHEN/THEN in the name) for consistency.

### 2. `runTmuxCommand` Should Be a Shared Utility

If `TmuxSessionManager` and `TmuxCommunicator` remain separate, they will both need the `runTmuxCommand` helper. The plan should explicitly address DRY here -- either extract a shared utility or use composition (one class delegates to the other, or both inject a `TmuxCommandRunner`).

### 3. Structured Logging

The plan does not show any `Out` logging calls. Per project standards, key operations (session creation, session kill, sending keys) should be logged via `Out` with `Val`/`ValType`. The implementer should add:
- `out.info("creating_tmux_session", Val(sessionName, ValType.SESSION_NAME))`
- `out.info("killing_tmux_session", Val(sessionName, ValType.SESSION_NAME))`
- `out.debug { listOf(Val(text, ValType.COMMAND_TEXT)) to "sending_keys_to_tmux" }`

### 4. `waitFor` Timeout

Consider adding a timeout to `process.waitFor()` calls for tmux commands. Tmux CLI operations should complete in milliseconds. A 10-second timeout prevents hanging if something goes wrong, rather than blocking indefinitely.

### 5. `InteractiveProcessRunner` Disposition

The plan says "Existing code preserved: InteractiveProcessRunner remains for direct TTY use case." This is correct. However, `App.kt` currently calls `InteractiveProcessRunner`. The plan should be explicit that the existing `InteractiveProcessRunner` call in `App.kt` will be **replaced** (not augmented) with the tmux-based flow. The plan says this under "Modified Files" but it should be clearer about whether `InteractiveProcessRunner` is still used anywhere or becomes dead code.

## Strengths

- **Clean SRP decomposition**: Session lifecycle vs. I/O is a natural boundary.
- **Correct tmux commands**: `new-session -d -s`, `has-session -t`, `kill-session -t`, `send-keys -t` are all correct and idiomatic.
- **PARETO-aligned**: Two small classes, direct ProcessBuilder calls, no abstraction ceremony. Gets to working code fast.
- **Good exploration**: The exploration correctly identified the key insight that tmux is fundamentally different from the TTY-passthrough approach.
- **Preserves existing code**: Not removing `InteractiveProcessRunner` is the right call -- it serves a different use case.
- **Test plan is reasonable**: Testing with `bash` session + file verification is a pragmatic approach that avoids needing `claude` auth in CI.

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**The plan can proceed to implementation without a plan iteration round.** The major concerns (shell escaping with `-l` flag, output capture for errors, cleanup enforcement) should be addressed during implementation. They are straightforward additions, not architectural changes.

### Signal: PLAN_ITERATION can be SKIPPED.
