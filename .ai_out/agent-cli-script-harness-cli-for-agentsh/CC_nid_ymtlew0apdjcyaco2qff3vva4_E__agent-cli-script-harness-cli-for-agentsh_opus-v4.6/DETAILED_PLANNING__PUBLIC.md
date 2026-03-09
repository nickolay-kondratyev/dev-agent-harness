# Detailed Implementation Plan -- harness-cli-for-agent.sh

## 1. Problem Understanding

**Goal**: Create a bash CLI script (`scripts/harness-cli-for-agent.sh`) that agents running inside TMUX sessions use to communicate back to the Chainsaw harness via HTTP. This is the "Agent to Harness" half of the bidirectional communication channel.

**Key Constraints**:
- Must be pure bash glue wrapping `curl` -- KISS
- Port discovery is file-based (`$HOME/.chainsaw_agent_harness/server/port.txt`)
- All requests are JSON POSTs with a `branch` field derived from `git branch --show-current`
- The `question` subcommand blocks (curl naturally blocks until server responds)
- `--help` output will be embedded in agent instructions wrapped in `<critical_to_keep_through_compaction>` tags
- No `scripts/` directory exists yet -- needs creation

**Assumptions**:
- `curl`, `git`, and `jq` are available on the agent's PATH (standard dev environment)
- The script itself will be placed on agent PATH by the harness before agent launch
- Server endpoints are not yet implemented -- this script is designed ahead of the server

---

## 2. High-Level Architecture

This is a single bash script with a simple dispatch pattern:

```
harness-cli-for-agent.sh <subcommand> [args]
         |
         v
   Read port from file
         |
         v
   Get git branch
         |
         v
   Construct JSON body
         |
         v
   curl POST to http://localhost:${PORT}/agent/${subcommand}
```

**Components**:
1. `scripts/harness-cli-for-agent.sh` -- the CLI script
2. `scripts/test_harness_cli.sh` -- shell-based test script

---

## 3. Implementation Phases

### Phase 1: Create the CLI Script

**Goal**: Deliver a working `scripts/harness-cli-for-agent.sh` with all four subcommands and help.

**Files**: New `scripts/harness-cli-for-agent.sh`

**Key Steps**:

1. Create `scripts/` directory.

2. Create `scripts/harness-cli-for-agent.sh` with the following structure:

   a. **Shebang and strict mode**: `#!/usr/bin/env bash` + `set -euo pipefail` (matches `test.sh` convention).

   b. **Anchor point**: Add `ref.ap.XXX.E` comment near the top referencing the design ticket's "Agent CLI Script" section. Generate the anchor point as part of ticket closure (per ticket instructions).

   c. **Constants**:
      - `PORT_FILE="$HOME/.chainsaw_agent_harness/server/port.txt"` -- named constant, no magic strings.

   d. **Helper function `_read_port`**:
      - Check if `PORT_FILE` exists. If not, print error to stderr: `"ERROR: Harness server not running. Port file not found at [${PORT_FILE}]"` and exit 1.
      - Read the port value. Validate it is a number (simple regex check). Exit 1 with clear message if invalid.
      - Print (echo) the port to stdout.

   e. **Helper function `_get_branch`**:
      - Run `git branch --show-current`.
      - If result is empty (detached HEAD), print error to stderr and exit 1.
      - Print branch to stdout.

   f. **Helper function `_post`**:
      - Parameters: `endpoint` (string), `json_body` (string).
      - Constructs the full URL: `http://localhost:${PORT}/agent/${endpoint}`.
      - Executes: `curl --silent --fail --show-error -X POST -H "Content-Type: application/json" -d "${json_body}" "${url}"`.
      - `--fail` ensures non-2xx HTTP responses produce a non-zero exit code.
      - `--silent` suppresses progress bar; `--show-error` still shows errors.
      - Print the response body to stdout (useful for `question` which returns an answer).

   g. **`show_help` function**: Print usage text to stdout. The help text should be self-contained and clear enough to embed in agent instructions. Structure:

      ```
      Usage: harness-cli-for-agent.sh <command> [args]

      Commands:
        done                   Signal task completion to the harness.
        question "<text>"      Ask the harness a question. Blocks until answered.
        failed "<reason>"      Signal unrecoverable failure to the harness.
        status                 Reply to a health ping from the harness.

      Options:
        --help, -h             Show this help message.

      Environment:
        Port is read from $HOME/.chainsaw_agent_harness/server/port.txt
        Branch is detected via 'git branch --show-current'
      ```

   h. **Main dispatch (`main` function)**:
      - If `$#` is 0 or first arg is `--help` / `-h`: call `show_help` and exit 0.
      - Read port via `_read_port`.
      - Read branch via `_get_branch`.
      - `case "$1"` dispatch:
        - `done`: `_post "done" '{"branch":"'"${BRANCH}"'"}'`
        - `question`: Require `$2` (error if missing: `"ERROR: question command requires a text argument"`). `_post "question" '{"branch":"'"${BRANCH}"'","question":"'"${QUESTION}"'"}'`
        - `failed`: Require `$2` (error if missing: `"ERROR: failed command requires a reason argument"`). `_post "failed" '{"branch":"'"${BRANCH}"'","reason":"'"${REASON}"'"}'`
        - `status`: `_post "status" '{"branch":"'"${BRANCH}"'"}'`
        - `*`: Print `"ERROR: Unknown command [${1}]"` to stderr, call `show_help`, exit 1.
      - Call `main "${@}"` at the bottom.

3. **Make executable**: `chmod +x scripts/harness-cli-for-agent.sh`.

**JSON Escaping Consideration**: The `question` and `failed` arguments could contain characters that break JSON (quotes, backslashes, newlines). The simplest robust approach is to use a small `jq` one-liner to construct JSON safely rather than manual string interpolation. This avoids an entire class of bugs.

   Recommended pattern for JSON construction:
   ```bash
   json_body=$(jq -n --arg branch "$BRANCH" --arg question "$QUESTION" \
     '{branch: $branch, question: $question}')
   ```
   This handles all escaping automatically. If `jq` availability is a concern, document it as a prerequisite. Given this runs in a dev environment where `jq` is standard, this is the right tradeoff (robust over clever).

**Verification**: Run `scripts/harness-cli-for-agent.sh --help` and confirm output is clear and complete.

---

### Phase 2: Create the Test Script

**Goal**: Deliver `scripts/test_harness_cli.sh` covering help output, error handling, and curl construction.

**Files**: New `scripts/test_harness_cli.sh`

**Key Steps**:

1. Create `scripts/test_harness_cli.sh` with `#!/usr/bin/env bash` + `set -euo pipefail`.

2. **Test framework**: No external test framework. Use simple assertion functions:
   - `assert_equals "expected" "actual" "description"` -- compare strings, print PASS/FAIL.
   - `assert_contains "haystack" "needle" "description"` -- substring check.
   - `assert_exit_code expected_code "command..." "description"` -- run command, check exit code.
   - Track pass/fail counts. Exit 1 if any test failed.

3. **Test Categories**:

   **a. Help output tests**:
   - `--help` exits 0.
   - `--help` output contains "Usage:" string.
   - `--help` output contains all four commands: `done`, `question`, `failed`, `status`.
   - `-h` also produces help (alias).
   - No arguments produces help.

   **b. Port file missing tests**:
   - When port file does not exist, script exits non-zero.
   - When port file does not exist, stderr contains "not found" or "not running" (clear error message).
   - Use a temp `HOME` override: `HOME=$(mktemp -d)` so the test does not accidentally find a real port file.

   **c. Missing argument tests**:
   - `question` without text argument: exits non-zero, stderr contains error about missing argument.
   - `failed` without reason argument: exits non-zero, stderr contains error about missing argument.

   **d. Unknown command test**:
   - `harness-cli-for-agent.sh bogus`: exits non-zero, stderr mentions unknown command.

   **e. Curl command construction tests (DRY_RUN approach)**:
   - Add a `HARNESS_CLI_DRY_RUN` env var check in the `_post` function. When `HARNESS_CLI_DRY_RUN=true`, instead of executing curl, print the full curl command to stdout and exit 0. This is a test hook -- it is NOT a silent fallback. It is explicitly opt-in and clearly named.
   - For each subcommand, set up a fake port file (write "12345" to `$HOME/.chainsaw_agent_harness/server/port.txt` in a temp HOME), set `HARNESS_CLI_DRY_RUN=true`, run the command, and verify:
     - URL contains `localhost:12345/agent/done` (or `question`, `failed`, `status`).
     - JSON body contains `"branch":"` with the current branch.
     - For `question`: JSON body contains `"question":"some test text"`.
     - For `failed`: JSON body contains `"reason":"some reason"`.

4. **Test script execution pattern**:
   ```
   #!/usr/bin/env bash
   set -euo pipefail

   SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
   HARNESS_CLI="${SCRIPT_DIR}/harness-cli-for-agent.sh"

   # ... assertion helpers ...
   # ... test functions ...
   # ... run all tests ...
   # ... print summary, exit 1 if failures ...
   ```

5. Make executable: `chmod +x scripts/test_harness_cli.sh`.

**Verification**: Run `scripts/test_harness_cli.sh` and confirm all tests pass.

---

### Phase 3: Anchor Point and Cross-References

**Goal**: Create the anchor point per ticket completion criteria.

**Key Steps**:

1. Run `anchor_point.create` to generate a new AP.
2. Add the AP below the `### Agent CLI Script` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`.
3. Add `ref.ap.XXX.E` as a comment near the top of `harness-cli-for-agent.sh`.

---

## 4. Technical Considerations

### JSON Construction -- Use `jq`

Manual JSON string interpolation in bash is fragile. A question text like `How should I handle "special" chars?` would break naive interpolation. Use `jq -n --arg` for all JSON body construction. This is the single most important implementation detail to get right.

### Error Handling Strategy

| Scenario | Behavior |
|---|---|
| Port file missing | Exit 1, stderr: clear message with file path |
| Port file contains non-numeric value | Exit 1, stderr: "invalid port" message |
| Detached HEAD (no branch) | Exit 1, stderr: "cannot determine branch" |
| Missing required argument | Exit 1, stderr: which argument is missing |
| Unknown subcommand | Exit 1, stderr: "unknown command", print help |
| curl failure (server down, network error) | `set -e` + `curl --fail` propagates the error naturally |

### Exit Codes

- `0`: Success
- `1`: Client-side error (missing port file, bad args, unknown command)
- Non-zero from curl: Server-side or network error (propagated as-is)

### Logging Style

Per CLAUDE.md conventions for bash: `"ERROR: port file not found at [${PORT_FILE}]"` (bracket-wrapped values).

### DRY_RUN Test Hook

The `HARNESS_CLI_DRY_RUN` environment variable in `_post` is a deliberate, explicit test seam. It is:
- Opt-in only (never activates unless explicitly set)
- Named clearly (no surprise behavior)
- Does NOT mask failures -- it replaces the network call with a deterministic output for verification

This is preferable to mocking `curl` via PATH manipulation, which is fragile and hard to reason about.

---

## 5. Testing Strategy

### Test Coverage Matrix

| Test | Category | What it verifies |
|---|---|---|
| `--help` exits 0 | Help | Basic usability |
| `--help` contains all commands | Help | Completeness of help text |
| `-h` works | Help | Alias |
| No args shows help | Help | Default behavior |
| Port file missing -> exit 1 | Error | Fail-fast when server not running |
| Port file missing -> clear message | Error | Actionable error message |
| `question` without arg -> exit 1 | Validation | Required argument enforcement |
| `failed` without arg -> exit 1 | Validation | Required argument enforcement |
| Unknown command -> exit 1 | Validation | Invalid input handling |
| `done` constructs correct curl | DRY_RUN | URL + JSON body correctness |
| `question "text"` constructs correct curl | DRY_RUN | URL + JSON body with question field |
| `failed "reason"` constructs correct curl | DRY_RUN | URL + JSON body with reason field |
| `status` constructs correct curl | DRY_RUN | URL + JSON body correctness |

### Edge Cases

- Question text with special characters (quotes, newlines) -- `jq` handles this
- Very long branch names -- no issue, just a JSON string
- Port file with trailing newline -- `read` or `tr -d '\n'` strips it

### What is NOT tested

- Actual HTTP communication with the server (that is an integration test for the server ticket)
- The server's response handling (server is not yet implemented)

---

## 6. Acceptance Criteria

1. `scripts/harness-cli-for-agent.sh --help` prints usage text containing all four subcommands and exits 0.
2. Running any subcommand without a port file at `$HOME/.chainsaw_agent_harness/server/port.txt` exits non-zero with a clear error message to stderr.
3. `question` and `failed` subcommands require a second argument; missing it exits non-zero with an error.
4. Unknown subcommands exit non-zero with an error and print help.
5. With `HARNESS_CLI_DRY_RUN=true` and a valid port file, each subcommand prints the correct curl command with proper URL and JSON body.
6. JSON bodies are constructed safely (no injection from special characters in arguments).
7. `scripts/test_harness_cli.sh` passes all tests.
8. Anchor point created and cross-referenced per ticket completion criteria.
9. Script is executable (`chmod +x`).

---

## 7. Open Questions / Decisions

None. All requirements are clear from the clarification and exploration phases. This is straightforward glue code.
