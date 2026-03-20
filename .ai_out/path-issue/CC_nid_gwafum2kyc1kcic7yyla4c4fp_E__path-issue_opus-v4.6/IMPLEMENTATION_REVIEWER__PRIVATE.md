# Implementation Reviewer: Private Context

## Review Methodology

1. Read all context files (exploration, plan, plan review, implementation public) in parallel.
2. Read the new test file and all pattern reference files in parallel.
3. Read source files for `ClaudeCodeAdapter`, `AgentTypeAdapter`, `BuildStartCommandParams`,
   `CallbackScriptsDir`, `TmuxStartCommand` to understand the full command-building pipeline.
4. Ran `./gradlew :app:test` to verify unit tests pass (confirmed BUILD SUCCESSFUL).
5. Verified integ test gating by checking JUnit XML (1 skipped, 0 failures when run without
   `-PrunIntegTests=true`).
6. Verified integ test passing via committed test results in `app/test-results/`.
7. Checked `git diff main...HEAD` to confirm no existing tests or production code were modified.
8. Traced the command replacement logic step by step through `ClaudeCodeAdapter.buildStartCommand()`,
   `escapeForBashC()`, and the test's string manipulation.

## Key Verification Points

### Command Replacement Correctness
- `escapeForBashC` only replaces `'` -> `'\''`. The marker `claude --model` has no single quotes,
  so it survives escaping.
- `substring(0, markerIndex)` captures `bash -c '...&& ` (everything before `claude`).
- Appending `echo $PATH > <file>'` replaces the claude command and provides the closing quote.
- Inside `bash -c '...'`, `$PATH` is literal for the outer shell but expanded by the inner bash.

### Shell Quoting Analysis
- The `\$PATH` in Kotlin string `"echo \$PATH > ..."` produces `echo $PATH > ...` at runtime.
- Inside the `bash -c '...'` single-quoted wrapper, this `$PATH` is literal (outer shell does not
  expand it). The inner bash (invoked by `-c`) then expands `$PATH` when executing the command.
- This matches exactly how the production `export PATH=$PATH:<dir>` works in the same wrapper.

### Edge Case: Output File Path with Spaces
- If `System.getProperty("user.dir")` contains spaces, the `echo $PATH > <path>` would fail
  because the path is not quoted. However, this is the same pattern used in production
  (`cd ${params.workingDir}` is also unquoted). In practice, Docker containers use paths
  without spaces, so this is not a real concern.

## Items Considered But Not Flagged

1. **`CallbackScriptsDir` import used only as type annotation**: Explicit type annotations are
   encouraged by the codebase's "be explicit" philosophy. The import is used and the annotation
   adds clarity.

2. **Magic number `99999` for server port**: This is a dummy value that is never used (the test
   replaces the claude command). It appears in the exported env var but is irrelevant to the
   PATH assertion. Not worth extracting to a constant for a single-use test-only value.

3. **`SharedContextDescribeSpec` coupling to GLM/ZAI config**: The plan review explicitly
   considered and accepted this tradeoff for consistency with existing tests. Not flagged.

4. **Single `it` block**: The plan review recommended simplifying to one assertion. The
   implementation followed this correctly. `capturedPath shouldContain callbackScriptsDir.path`
   is the single, focused assertion.
