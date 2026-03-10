# Logical Review: harness-cli-for-agent.sh

**Branch**: `CC_nid_ymtlew0apdjcyaco2qff3vva4_E__agent-cli-script-harness-cli-for-agentsh_opus-v4.6`
**Divergence**: `1cb6a62365c673d108df508a579f22434fc320e6`
**Date**: 2026-03-10
**Verdict**: READY (with one IMPORTANT item to note for follow-up)

---

## Summary

This PR adds `scripts/harness-cli-for-agent.sh` — a bash script enabling agents running in TMUX sessions to POST HTTP to the Chainsaw harness. Four subcommands: `done`, `question`, `failed`, `status`. Accompanied by `scripts/test_harness_cli.sh` with 53 passing shell tests.

Tests ran clean:
- Shell test suite: 53/53 passed
- Kotlin/Gradle test suite: BUILD SUCCESSFUL, all UP-TO-DATE

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. ALL commands have no curl timeout — liveness risk

`/scripts/harness-cli-for-agent.sh`, lines 67-71:
```bash
curl --silent --fail-with-body --show-error \
  -X POST \
  -H "Content-Type: application/json" \
  -d "${json_body}" \
  "${url}"
```

No `--max-time` is set. All four commands — `done`, `failed`, `status`, and `question` — are fire-and-forget HTTP POSTs. `question` does NOT block waiting for an answer via curl; the answer is delivered back to the agent asynchronously via TMUX `send-keys`. All commands are expected to complete quickly.

Without `--max-time`, any hung harness will block the agent's curl indefinitely on any command, with no escape. `--max-time` should be added universally. This is likely a follow-up ticket since it requires a deliberate decision on timeout values, but it must not be forgotten.

### 2. `HARNESS_CLI_DRY_RUN` test seam is a live environment risk

`/scripts/harness-cli-for-agent.sh`, lines 58-62:
```bash
if [[ "${HARNESS_CLI_DRY_RUN:-}" == "true" ]]; then
    echo "URL=${url}"
    echo "BODY=${json_body}"
    return 0
fi
```

If an agent's shell environment has `HARNESS_CLI_DRY_RUN=true` set (e.g., inherited from CI, a developer's shell, or a mis-configured agent container), all harness communication silently does nothing — no real HTTP calls, no errors, no warning that the seam is active. The agent would think it successfully completed its task while the harness received nothing.

This is an accepted trade-off for testability in shell scripts (no dependency injection), but it warrants documentation. At minimum, the script should emit a warning to stderr when DRY_RUN is active so production logs make the mode visible:

```bash
if [[ "${HARNESS_CLI_DRY_RUN:-}" == "true" ]]; then
    echo "[DRY_RUN] URL=${url}" >&2   # warn to stderr
    echo "URL=${url}"
    echo "BODY=${json_body}"
    return 0
fi
```

---

## Suggestions

### Missing test: empty port file

The test suite covers: port file missing, non-numeric value, value 0, value > 65535, and no trailing newline. An empty port file (0 bytes) is also possible (e.g., harness crashed mid-write). Manually verified this produces:

```
ERROR: Invalid port value [] in [/path/to/port.txt]
```

The error is technically correct but the empty brackets `[]` are confusing — there's no value to display. A targeted test and potentially a cleaner message ("Port file is empty") would improve diagnosability. Low priority since the error path is safe.

### Missing test: empty/whitespace-only question/reason text

`harness-cli-for-agent.sh question ""` passes the arity check (`$# -lt 2` passes with 2 args), constructs JSON with an empty string, and POSTs it. The harness server would receive `{"branch":"...","question":""}`. Whether that's valid is a server-side concern, but the CLI doesn't validate that the argument is non-empty. If this is undesirable, a non-empty check should be added. Currently untested.

---

## What Works Well

- Arity validation before I/O (port file read, git invocation) is correct and well-tested. Error messages are unambiguous regardless of environment state.
- `jq -n --arg` for JSON construction is correct — handles all special characters safely.
- `read -r port < "${PORT_FILE}" || true` with subsequent regex validation is the right fix for the `set -e` + EOF issue.
- Port validation rejects 0, leading zeros, and values above 65535.
- The `_get_branch` function correctly handles both git failures and detached HEAD state.
- Test isolation via `ORIGINAL_HOME` save/restore pattern is solid.
- The second `case "$1"` dispatch (lines 129-148) has no `*` wildcard, but this is safe since the first case already validated. Not a bug.
