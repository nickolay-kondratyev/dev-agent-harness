# Implementation Review: TmuxPathIntegTest

## Summary

A new focused integration test (`TmuxPathIntegTest`) was added to verify that the `PATH` environment
variable is correctly set inside a tmux session when using the command built by
`ClaudeCodeAdapter.buildStartCommand()`. This fills a legitimate testing gap between unit tests
(which check the command string) and heavy integration tests (which require a real LLM agent).

**Only one new file was added. No existing tests or production code were modified or removed.**

**Overall assessment**: Solid implementation that follows established patterns. One minor code quality
issue found. No critical or important issues.

## CRITICAL Issues

None.

## IMPORTANT Issues

None.

## Suggestions

### 1. Fully-qualified `TmuxStartCommand` reference should be an import

**File**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/TmuxPathIntegTest.kt`, line 101

The code uses a fully-qualified class name inline:
```kotlin
com.glassthought.shepherd.core.agent.data.TmuxStartCommand(modifiedCommand)
```

The existing `TmuxCommunicatorIntegTest` (the pattern reference for this test) uses a proper import
instead:
```kotlin
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
```

**Suggestion**: Add the import and use `TmuxStartCommand(modifiedCommand)` directly. This is a
consistency issue with the established pattern, not a functional problem.

## Detailed Analysis

### Correctness

The command replacement logic is correct. Here is the reasoning:

1. `ClaudeCodeAdapter.buildStartCommand()` produces:
   `bash -c '...export PATH=$PATH:<dir> && claude --model sonnet ...'`

2. `escapeForBashC` only escapes single quotes (`'` -> `'\''`). The marker `claude --model` contains
   no single quotes, so it survives escaping and is findable in the final command.

3. `originalCommand.substring(0, markerIndex)` captures everything up to (but excluding) `claude --model`,
   which includes the `&& ` separator.

4. The replacement appends `echo $PATH > <file>'`, providing the closing single quote that replaces
   the one from the original command. Result: `bash -c '...export PATH=$PATH:<dir> && echo $PATH > <file>'`

5. Inside single quotes, `$PATH` is literal text passed to the inner `bash -c`, which then expands it.
   This correctly captures the PATH as seen by the spawned process.

### Pattern Compliance

- Extends `SharedContextDescribeSpec` -- consistent with `TmuxCommunicatorIntegTest` and
  `TmuxSessionManagerIntegTest`.
- Gated with `isIntegTestEnabled()` at the top-level `describe` -- correct.
- `@OptIn(ExperimentalKotest::class)` annotation -- present.
- BDD GIVEN/WHEN/THEN structure -- followed.
- One assert per `it` block -- followed (single `shouldContain` assertion).
- `afterEach` cleanup with `createdSessions` and `createdFiles` -- exact match to
  `TmuxCommunicatorIntegTest` pattern.

### Robustness / Flakiness

- The `echo $PATH > <file>` command executes immediately and the tmux session exits. Polling with
  `AsgardAwaitility.wait().atMost(5.seconds)` is generous for this case. No flakiness concern.
- The session name uses `System.currentTimeMillis()` for uniqueness, preventing collisions between
  parallel test runs.
- The output file also uses `System.currentTimeMillis()` for uniqueness.
- The `require(markerIndex > 0)` guard provides a clear failure message if the adapter's command
  format changes in the future, rather than producing a cryptic substring error.

### Cleanup

- `afterEach` kills tmux sessions and deletes temp files.
- `try/catch` around session killing handles the case where the session has already exited (the
  `echo` command exits immediately, so the session will be dead by cleanup time).
- `.tmp/` directory creation via `outputFile.parentFile.mkdirs()` -- correct.

### No Loss of Functionality

- No existing tests were modified or removed. Confirmed via `git diff main...HEAD` filtering
  for Kotlin files excluding the new test.
- No production code was changed.

### Unit Test Suite

- When run without `-PrunIntegTests=true`, the test is properly skipped (verified via JUnit XML:
  `tests="1" skipped="1" failures="0"`).
- The committed test results show the test passing when run with the integ flag.

## Verdict

**APPROVED**

The implementation is clean, correct, follows all established patterns, and fills a genuine testing
gap. The single suggestion (import style for `TmuxStartCommand`) is cosmetic and does not block
approval.
