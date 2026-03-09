# Implementation Review -- harness-cli-for-agent.sh

## Summary

The implementation delivers a clean, KISS-aligned bash CLI script (`scripts/harness-cli-for-agent.sh`) with a comprehensive test suite (`scripts/test_harness_cli.sh`). All 37 tests pass. All 5 reviewer refinements from the plan review were incorporated. The code is well-structured, uses `jq` for safe JSON construction, and follows project conventions (bracket-wrapped values in error messages, `set -euo pipefail` with WHY comment, etc.).

Overall assessment: **APPROVED WITH ONE BUG FIX REQUIRED**. There is one correctness bug related to `read -r` with `set -e` that must be fixed before merge. There is also one completion criteria item not yet done (anchor point cross-reference in design ticket).

---

## CRITICAL Issues

### 1. BUG: `read -r port < "${PORT_FILE}"` exits silently when port file lacks trailing newline

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/scripts/harness-cli-for-agent.sh`, line 23

**Problem**: When the port file does not end with a newline character, `read -r` returns exit code 1 (EOF reached without a delimiter). With `set -e` active, the script exits immediately with no error message. The port validation on line 25 is never reached.

This is a real-world concern because Kotlin's `File.writeText("12345")` does NOT add a trailing newline. If the harness server writes the port file this way, the CLI script will silently fail every time.

**Verified**: Tested with `printf "12345" > port.txt` -- script exits with code 1, no error output.

**Fix**:
```bash
# [read -r]: naturally strips trailing newline from port file.
# [|| true]: read returns 1 at EOF when file lacks a trailing newline;
# suppress to let the regex validation below handle empty/invalid values.
read -r port < "${PORT_FILE}" || true
```

**Test gap**: The test's `setup_temp_home_with_port` uses `echo "12345"` which always adds a trailing newline, so this bug is masked. Add a test case with `printf "12345"` (no trailing newline) to cover this.

---

## IMPORTANT Issues

### 1. Anchor point not cross-referenced in design ticket (completion criteria #2)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`, line 110

**Problem**: The ticket's completion criteria state: "Add `ap.XXX.E` just below the `### Agent CLI Script` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`." The anchor point `ap.8PB8nMd93D3jipEWhME5n.E` was created and placed in the script, but the cross-reference was NOT added to the design ticket.

**Fix**: Add a line below `### Agent CLI Script` (line 110):
```markdown
### Agent CLI Script
<!-- ap.8PB8nMd93D3jipEWhME5n.E -->
```

### 2. Missing test: invalid port value in port file

**Problem**: The error handling table in the plan lists "Port file contains non-numeric value" as a defined behavior (exit 1 with error message). The code handles this correctly (lines 25-28 of harness-cli-for-agent.sh), but there is no test verifying this behavior.

**Fix**: Add a test case that writes a non-numeric value (e.g., "abc" or "not_a_port") to the port file and verifies exit code 1 and a clear error message on stderr.

---

## Suggestions

### 1. Declare `local json_body` once at top of `main`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/scripts/harness-cli-for-agent.sh`, lines 101, 110, 120, 126

Currently `local json_body` is declared in four separate case branches. While functionally harmless (only one branch executes), it is a minor DRY violation. Declare it once before the `case` statement:

```bash
local json_body
case "$1" in
  done)
    json_body=$(jq -n --arg branch "${BRANCH}" '{branch: $branch}')
    _post "done" "${json_body}"
    ;;
  ...
```

### 2. Consider validating non-empty `$2` for `question` and `failed`

Currently, `question ""` (empty string argument) is accepted and sends `{"question": ""}` to the server. This is probably harmless since the server can reject it, but an early validation `[[ -z "$2" ]]` would catch accidental empty arguments with a clear error message.

### 3. Test cleanup robustness -- consider a trap

The tests manually save/restore `HOME` and `rm -rf "${TEMP_HOME}"` at the end of each test function. If a new assertion were added that accidentally triggers `set -e` (unlikely with current assertion functions, but possible in future edits), the cleanup would be skipped. A `trap` would make cleanup more robust:

```bash
# At the start of each test that modifies HOME:
trap 'export HOME="${ORIGINAL_HOME}"; rm -rf "${TEMP_HOME}"; unset HARNESS_CLI_DRY_RUN 2>/dev/null' RETURN
```

This is optional -- the current cleanup pattern works for the existing code.

---

## Reviewer Refinements Verification

| # | Refinement | Incorporated? |
|---|---|---|
| 1 | DRY_RUN prints URL and BODY as labeled lines (not curl command) | YES -- `_post` prints `URL=...` and `BODY=...` |
| 2 | WHY comment for `set -euo pipefail` | YES -- both scripts have the comment |
| 3 | `read -r port < "${PORT_FILE}"` for port reading | YES -- but see CRITICAL bug above |
| 4 | `_get_branch` wraps with context-adding error handler | YES -- catches git failure and adds context |
| 5 | Shared test setup helpers (`setup_temp_home`, `setup_temp_home_with_port`) | YES -- DRY helpers extracted |

---

## Plan Adherence

The implementation matches the approved plan. All four subcommands (`done`, `question`, `failed`, `status`) are implemented. `jq` is used for all JSON construction. Help output is clear and contains all commands. Error messages use bracket-wrapped values. The DRY_RUN test seam is clean and explicit.

---

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH REQUIRED CHANGES
- [ ] NEEDS REVISION
- [ ] REJECTED

**Required before merge**:
1. Fix the `read -r` bug (add `|| true`) and add a test for port file without trailing newline.
2. Add the anchor point cross-reference in the design ticket.
3. Add test for invalid port value in port file.

**Optional improvements**:
- Declare `local json_body` once in `main`.
- Consider validating non-empty `$2`.
