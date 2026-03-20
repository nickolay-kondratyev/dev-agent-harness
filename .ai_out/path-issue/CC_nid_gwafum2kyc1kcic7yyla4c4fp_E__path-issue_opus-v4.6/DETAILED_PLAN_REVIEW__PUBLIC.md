# Plan Review: Integration Test for PATH in tmux Sessions

## Executive Summary

The plan is well-researched, correctly identifies the testing gap, and proposes a sound approach.
The command replacement strategy is the right idea but needs a minor correction to handle the
`escapeForBashC` transformation. The plan is **APPROVED WITH MINOR REVISIONS** -- the revisions
below are inline adjustments, not blocking redesigns.

## Critical Issues (BLOCKERS)

None.

## Major Concerns

### 1. Command Replacement Strategy Needs Precision

- **Concern**: The plan proposes finding `claude --model` in the command string and replacing
  everything from there to the closing `'`. This is correct in principle, but the plan's analysis
  of shell quoting is needlessly convoluted (Section 4, Phase 2 spends ~40 lines going back and
  forth on `$PATH` escaping). The final conclusion is correct: inside `bash -c '...'`, `$PATH`
  is literal and the inner bash expands it.

- **Why**: The implementation engineer needs a crisp, unambiguous replacement recipe. The
  back-and-forth analysis could lead to confusion.

- **Suggestion**: The replacement should be stated once, clearly:
  ```
  Find "claude --model" in command.command.
  Replace from "claude --model" to the final "'" (exclusive) with:
    echo $PATH > <tmpFilePath>
  Result: bash -c '...export PATH=$PATH:<dir> && echo $PATH > <tmpFilePath>'
  ```
  That is all. No need to discuss `\\$PATH` or `\$PATH` -- the replacement text goes into the
  already-escaped inner command, and `$PATH` is already literal inside single quotes.

### 2. The `escapeForBashC` May Not Affect The Replacement Token

- **Concern**: The plan assumes `claude --model` will appear literally in the final command.
  This IS true because `escapeForBashC` only escapes single quotes (`'` -> `'\''`), and the
  `claude --model` substring contains no single quotes. Good.

- **However**: The plan does not mention this reasoning. The implementer should be aware that
  the search token `claude --model` survives `escapeForBashC` because it has no single quotes.

### 3. Second `it` Block ("THEN callback_shepherd.signal.sh is findable via PATH") -- Drop It

- **Concern**: The plan suggests a second assertion using `which callback_shepherd.signal.sh`
  or `command -v`. Then immediately says "keep it simple with one primary assertion."

- **Suggestion**: Remove the second `it` block entirely from the plan. It adds complexity
  (a second command replacement, or a different approach) and the value is marginal.
  `CallbackScriptsDir.validated()` already guarantees the script exists and is executable.
  The PATH test proves the directory is on PATH. That is sufficient.

  Final test structure should be:
  ```
  describe("GIVEN a tmux command built by ClaudeCodeAdapter with real CallbackScriptsDir")
      .config(isIntegTestEnabled())
      describe("WHEN the command is executed in a tmux session with claude replaced by echo PATH")
          it("THEN the captured PATH contains the callback scripts directory")
  ```
  One assertion. Clean and focused.

## Simplification Opportunities (PARETO)

### 1. Do NOT Extend SharedContextDescribeSpec -- Use AsgardDescribeSpec Directly

- **Current plan**: Extend `SharedContextDescribeSpec` to get `shepherdContext.infra.tmux.sessionManager`.

- **Problem**: `SharedContextDescribeSpec` triggers `SharedContextIntegFactory` which calls
  `ContextInitializer.forIntegTest()`. That reads `MY_ENV` env var, reads ZAI API key from disk,
  and creates a GLM config. This test does NOT need any of that -- it only needs tmux.

- **Counter-argument**: The existing `TmuxSessionManagerIntegTest` and
  `TmuxCommunicatorIntegTest` also extend `SharedContextDescribeSpec`. So the pattern IS
  established. Changing it just for this test would be inconsistent.

- **Verdict**: **Follow the existing pattern.** Use `SharedContextDescribeSpec`. The
  coupling to ZAI/GLM at class-load time is an existing design choice, and changing it
  is out of scope. The test requires `isIntegTestEnabled()` anyway, so the environment
  is expected to have the required env vars.

- **Value**: Consistency with existing integ test patterns.

### 2. Drop "THEN PATH is non-empty" Assertion

- **Current plan**: Two `it` blocks: one for "PATH contains scripts dir" and one for
  "PATH is non-empty" (sanity check).

- **Simpler**: If PATH contains the scripts dir, it is obviously non-empty. One assertion
  is sufficient: `capturedPath shouldContain callbackScriptsDir.path`.

- **Value**: One fewer `it` block, same coverage.

### 3. Temp File Location

- **Current plan**: `File(System.getProperty("user.dir"), ".tmp/path-integ-test-output-${System.currentTimeMillis()}")`.

- **Correct** per project convention. No change needed. Just confirming this is right.

## Minor Suggestions

1. **Session name**: Use a descriptive prefix like `"path-integ-"` plus timestamp, consistent
   with existing tests (`"test-comm-"`, `"test-exists-"`, etc.).

2. **Polling timeout**: 5 seconds is generous for a command that runs `echo` and exits.
   Consider 3 seconds. Not critical.

3. **Phase structure**: The plan has 4 phases but the actual implementation is a single test
   class with ~50 lines. "Phases" is over-structured for this scope. The implementer should
   just write the test class in one pass.

4. **The `ClaudeCodeAdapter` constructor**: The plan says to use `ClaudeCodeAdapter.create()`
   factory. But looking at the code, `create()` uses `FilesystemGuidScanner` which requires
   a real `claudeProjectsDir`. For this test, the adapter's `resolveSessionId` is never called,
   so using the internal constructor with `GuidScanner { emptyList() }` (as `ClaudeCodeAdapterTest`
   does) is simpler and avoids needing a real projects dir. However, `ClaudeCodeAdapter`'s
   primary constructor is `internal`, and the test class is in a different package
   (`com.glassthought.shepherd.integtest` vs `com.glassthought.shepherd.core.agent.adapter`).

   **Resolution**: Use `ClaudeCodeAdapter.create()` with `claudeProjectsDir = Path.of("/dev/null")`.
   The `FilesystemGuidScanner` will never be called. This matches the pattern in
   `ClaudeCodeAdapterTest` where `create()` is used with `/dev/null`.

   Wait -- `ClaudeCodeAdapterTest` actually uses both: `create()` with `/dev/null` for some tests
   and the `internal` constructor for others. But the internal constructor is only usable from the
   same module. Since the test IS in the same module (`app`), `internal` visibility allows access.
   But it's in a different PACKAGE.

   In Kotlin, `internal` means same module, NOT same package. So the `internal` constructor IS
   accessible from `com.glassthought.shepherd.integtest` because it's in the same `app` module.

   **Recommendation**: Use the `internal` constructor with `GuidScanner { emptyList() }` for
   simplicity. Avoids constructing a `FilesystemGuidScanner` that will never be used.

## Strengths

1. **Clear problem statement**: The gap between unit tests (command string) and heavy integ tests
   (full agent lifecycle) is well-identified.

2. **Right testing seam**: Replacing `claude --model ...` with `echo $PATH > file` tests the
   real command-building pipeline and real tmux session creation while avoiding LLM dependencies.
   This is the correct level of abstraction.

3. **Follows existing patterns**: Cleanup pattern (createdSessions/createdFiles with afterEach),
   `SharedContextDescribeSpec`, `isIntegTestEnabled()` gating, `AsgardAwaitility` polling -- all
   match existing tests.

4. **Acceptance criteria are clear and verifiable**: AC-1 through AC-10 are well-defined.

5. **Correct analysis of shell quoting**: Despite the back-and-forth, the final conclusion about
   `$PATH` being literal inside `bash -c '...'` is correct.

6. **No production code changes**: Test-only addition. Zero risk to production.

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

### Required Revisions (before implementation)

1. **Simplify to one `it` block**: Drop "PATH is non-empty" and "callback_shepherd.signal.sh
   findable via PATH". One assertion: `capturedPath shouldContain callbackScriptsDir.path`.

2. **Clean up Phase 2 analysis**: The replacement strategy section should state the approach
   once, clearly. Remove the back-and-forth analysis of `$PATH` escaping.

3. **Prefer internal constructor**: Use `ClaudeCodeAdapter(guidScanner = GuidScanner { emptyList() }, ...)`
   instead of `ClaudeCodeAdapter.create(claudeProjectsDir = Path.of("/dev/null"), ...)`.
   Simpler, no unused `FilesystemGuidScanner` construction.

These are minor -- the implementer can apply them during coding without a plan revision cycle.
