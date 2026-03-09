# Plan Review -- harness-cli-for-agent.sh

## Executive Summary

This is a well-structured plan for straightforward bash glue code. The plan is KISS-aligned, correctly identifies `jq` as the right tool for JSON construction, and has a thorough test strategy. There are no blocking issues. A few minor concerns around bash conventions, the DRY_RUN test seam naming, and a potential JSON escaping edge case are worth addressing before implementation.

**Recommendation**: APPROVED WITH MINOR REVISIONS. The revisions are small enough that PLAN_ITERATION can be skipped -- the implementor can incorporate these directly.

## Critical Issues (BLOCKERS)

None.

## Major Concerns

### 1. DRY_RUN prints the curl command but does not actually execute `jq` for JSON construction

- **Concern**: The plan says `_post` checks `HARNESS_CLI_DRY_RUN` to print the curl command instead of executing it. But the JSON body is constructed BEFORE `_post` is called (via `jq -n --arg`). The DRY_RUN test then verifies the JSON body content. This means `jq` is still executed during tests (which is fine and correct), but the plan's description of DRY_RUN in the `_post` function needs to clearly show that it prints/echoes the constructed URL and JSON body, NOT the literal curl command string. Printing a reconstructed `curl` command line reintroduces the escaping problem the plan explicitly avoids with `jq`.
- **Why**: If the test verifies a printed `curl -d '...'` string, special characters in the JSON could cause false test failures or misleading output.
- **Suggestion**: In DRY_RUN mode, print the URL and JSON body as separate clearly-labeled lines (e.g., `URL=...\nBODY=...`). This is simpler to assert against in tests and avoids shell-escaping traps in the printed curl command. Alternatively, just echo the JSON body to stdout (which is what curl would return anyway).

## Simplification Opportunities (PARETO)

### 1. Test assertion helpers -- keep minimal

The plan defines three assertion helpers (`assert_equals`, `assert_contains`, `assert_exit_code`). This is the right level. Do not add more. The `assert_exit_code` helper should capture both stdout and stderr of the command to allow subsequent content assertions without re-running the command. Consider a pattern like:

```bash
run_capturing() {
  local exit_code=0
  CAPTURED_STDOUT=$("$@" 2>>"${CAPTURED_STDERR_FILE}") || exit_code=$?
  CAPTURED_EXIT_CODE=$exit_code
}
```

This avoids running the command twice (once for exit code, once for output) and keeps tests fast and deterministic.

### 2. Temp HOME setup -- extract a helper

Multiple test categories need a temp HOME with or without a port file. Extract a `setup_temp_home` and `setup_temp_home_with_port` helper function to keep tests DRY. The plan implies this but does not explicitly call it out as a shared setup function.

## Minor Suggestions

### 1. Strict mode convention

The plan says to use `set -euo pipefail` (matching `test.sh`). This is correct for `harness-cli-for-agent.sh` since it runs independently on the agent PATH without the thorg shell environment. However, note that `run.sh`, `sanity_check.sh`, and `CLAUDE.generate.sh` use `# __enable_bash_strict_mode__` (a function from the sourced glassthought-bash-env). The implementor should add a brief comment explaining why `set -euo pipefail` is used directly:

```bash
# [set -euo pipefail]: Used directly (not __enable_bash_strict_mode__) because
# this script runs standalone on agent PATH without thorg shell env.
set -euo pipefail
```

### 2. Port file trailing newline handling

The plan mentions `read` or `tr -d '\n'` for stripping trailing newlines from the port file. Prefer `read -r PORT < "${PORT_FILE}"` which naturally strips the trailing newline. Document this in the `_read_port` function.

### 3. `_get_branch` failure mode

The plan handles detached HEAD (empty branch). Also consider the case where `git` is not available or the script is run outside a git repo. `set -e` will catch `git` command failure, but the error message from git alone may be confusing. A brief wrapper that catches the failure and adds context would be helpful:

```bash
BRANCH=$(git branch --show-current 2>/dev/null) || {
  echo "ERROR: Failed to determine git branch. Are you in a git repository?" >&2
  exit 1
}
```

### 4. Phase 3 anchor point -- minor ordering clarification

Phase 3 says "Run `anchor_point.create`" as part of implementation. This should happen as one of the last steps during ticket closure, not during initial implementation. The plan's Phase 3 section title ("Anchor Point and Cross-References") implies it is a separate final phase, which is correct. Just ensure the implementor knows this happens at the end.

### 5. Help text -- consider including version/script name

The help output could include the script basename for copy-paste clarity:

```
harness-cli-for-agent.sh -- Agent-to-Harness CLI

Usage: harness-cli-for-agent.sh <command> [args]
...
```

This is a nice-to-have, not a requirement.

### 6. `_post` should print response body only for `question`

The plan says `_post` prints the response body to stdout. For `done`, `failed`, and `status`, the response body is not meaningful to the agent. Consider only printing the response for `question` and suppressing output for others. This prevents unexpected output from confusing agent parsing. However, this is a minor point -- if the server returns empty bodies for non-question endpoints (which is likely), it is a non-issue.

## Strengths

- **JSON construction via `jq`**: This is the single most important decision in the plan and it is correct. Manual JSON interpolation in bash is a class of bugs waiting to happen. The plan identifies this clearly and even provides the exact `jq -n --arg` pattern.

- **Error handling table**: The error handling strategy table (Section 4) is clear, comprehensive, and well-structured. Every failure mode has a defined behavior.

- **DRY_RUN test seam**: Using an explicit environment variable as a test seam is pragmatic and transparent. It is clearly named, opt-in, and does not mask failures. This is far better than PATH manipulation to mock `curl`.

- **Test coverage matrix**: The 13-test matrix covers all meaningful behaviors without over-testing. It correctly excludes actual HTTP communication (which belongs to server integration tests).

- **Phased implementation**: Three clean phases with clear deliverables. Simple and easy to follow.

- **KISS throughout**: The plan stays true to "this is glue code wrapping curl." No over-engineering, no unnecessary abstractions.

- **Logging style**: Bracket-wrapped values in error messages (`[${PORT_FILE}]`) follow the project's bash logging convention.

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**Summary of revisions needed** (all minor, can be incorporated by implementor without plan iteration):

1. Clarify DRY_RUN output format: print URL and JSON body as labeled lines, not a reconstructed curl command string.
2. Add a WHY comment explaining `set -euo pipefail` vs `__enable_bash_strict_mode__`.
3. Use `read -r PORT < "${PORT_FILE}"` for port file reading.
4. Wrap `git branch --show-current` with a context-adding error handler.
5. Extract shared test setup helpers (`setup_temp_home`, `setup_temp_home_with_port`) to keep tests DRY.

**PLAN_ITERATION can be skipped.** These are implementation-level adjustments, not design changes.
