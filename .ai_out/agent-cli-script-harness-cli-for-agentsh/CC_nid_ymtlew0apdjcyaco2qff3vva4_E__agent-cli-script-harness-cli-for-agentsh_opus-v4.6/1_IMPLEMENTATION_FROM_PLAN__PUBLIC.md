# Implementation Report -- harness-cli-for-agent.sh

## Summary

Successfully implemented `scripts/harness-cli-for-agent.sh` (Agent-to-Harness CLI) and `scripts/test_harness_cli.sh` (test suite). All 37 tests pass. Existing Gradle tests pass (BUILD SUCCESSFUL).

## Files Created

1. **`scripts/harness-cli-for-agent.sh`** -- The Agent-to-Harness CLI script (ap.8PB8nMd93D3jipEWhME5n.E)
2. **`scripts/test_harness_cli.sh`** -- Shell-based test suite (37 tests)

## Reviewer Refinements Incorporated

All 5 reviewer refinements from DETAILED_PLAN_REVIEW were incorporated:

1. **DRY_RUN output format**: `_post` prints `URL=...` and `BODY=...` as separate labeled lines, not a reconstructed curl command string. Tests assert against these labeled lines.

2. **WHY comment for strict mode**: Both scripts include the comment:
   ```bash
   # [set -euo pipefail]: Used directly (not __enable_bash_strict_mode__) because
   # this script runs standalone on agent PATH without thorg shell env.
   ```

3. **Port file reading**: Uses `read -r port < "${PORT_FILE}"` with a WHAT comment explaining the newline stripping behavior:
   ```bash
   # [read -r]: naturally strips trailing newline from port file
   read -r port < "${PORT_FILE}"
   ```

4. **Git branch error handling**: `_get_branch` wraps `git branch --show-current` with context-adding error handler:
   ```bash
   branch=$(git branch --show-current 2>/dev/null) || {
     echo "ERROR: Failed to determine git branch. Are you in a git repository?" >&2
     exit 1
   }
   ```

5. **Test DRY helpers**: Extracted `setup_temp_home` and `setup_temp_home_with_port` shared setup functions used across all test categories.

## Test Coverage (37 tests)

| Category | Tests | Count |
|---|---|---|
| Help output | `--help` exits 0, contains Usage/all commands, `-h` works, no args shows help | 10 |
| Port file missing | exits 1, stderr has "not found", stderr has "Port file" | 3 |
| Missing arguments | `question` without arg, `failed` without arg | 4 |
| Unknown command | exits 1, mentions unknown command, includes command name | 3 |
| DRY_RUN: done | exit code, URL correctness, branch in body | 3 |
| DRY_RUN: question | exit code, URL, question field, question text | 4 |
| DRY_RUN: failed | exit code, URL, reason field, reason text | 4 |
| DRY_RUN: status | exit code, URL, branch in body | 3 |
| DRY_RUN: special chars | exit code, URL, escaped quotes in JSON | 3 |

## Implementation Decisions

- **`jq -n --arg` for all JSON construction**: Handles escaping of quotes, backslashes, and special characters automatically. This is the critical safety decision for the script.

- **`HARNESS_CLI_DRY_RUN` test seam**: Explicitly opt-in env var in `_post` function. When set to "true", prints URL and JSON body as labeled lines instead of executing curl. This is NOT a silent fallback -- it is a deliberate, named test hook.

- **Arithmetic increment pattern**: Used `PASS_COUNT=$((PASS_COUNT + 1))` instead of `((PASS_COUNT++))` because the latter returns exit code 1 when the variable is 0 (falsy in arithmetic context), which triggers `set -e`.

- **`run_capturing` helper**: Captures stdout, stderr, and exit code in a single invocation to avoid running commands twice. Uses a temp file for stderr since bash cannot capture both stdout and stderr into separate variables from a single command.

## Verification

```
$ scripts/test_harness_cli.sh
  Results: 37 passed, 0 failed

$ scripts/harness-cli-for-agent.sh --help
  (clean help output with all 4 commands documented)

$ ./gradlew :app:test
  BUILD SUCCESSFUL
```
