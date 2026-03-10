# Review: harness-cli-for-agent.sh

## Verdict: NOT READY

Two issues must be addressed before this is production-worthy. All 42 tests pass. The script is mostly well-written, but the two issues below will cause real problems in realistic operation.

---

## Issues Found

### [MAJOR] `_post()` has a hidden contract on a global variable it does not own

**File:** `scripts/harness-cli-for-agent.sh`, line 53

```bash
_post() {
  local endpoint="$1"
  local json_body="$2"
  local url="http://localhost:${PORT}/agent/${endpoint}"   # <-- PORT not a param
  ...
}
```

`PORT` is declared as `local PORT` inside `main()` (line 95). In bash, `local` variables use dynamic scoping — they ARE visible to functions called from the declaring scope. `_post()` silently depends on being called from `main()` (or any caller that has set `PORT` in its stack frame). `_post()` itself declares no such dependency.

**Why it matters:** With `set -u` active, calling `_post` from any other context — a future utility function, a test that sources the script, a wrapper — crashes immediately with `PORT: unbound variable`. The function's signature lies: it takes two arguments but secretly requires a third piece of ambient state. This is a SRP violation: `_post` should either receive PORT as a parameter, or read it internally.

**Fix:** Pass PORT as a parameter to `_post`, or read it inside `_post`.

```bash
_post() {
  local endpoint="$1"
  local json_body="$2"
  local port="$3"
  local url="http://localhost:${port}/agent/${endpoint}"
  ...
}
# Call site:
_post "done" "${json_body}" "${PORT}"
```

---

### [MAJOR] Argument validation runs AFTER port-file read and git invocation

**File:** `scripts/harness-cli-for-agent.sh`, lines 95–134

```bash
main() {
  ...
  local PORT
  PORT=$(_read_port)       # side effect: reads file, may exit 1

  local BRANCH
  BRANCH=$(_get_branch)    # side effect: invokes git, may exit 1

  case "$1" in
    question)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: question command requires a text argument" >&2
        exit 1
      fi
```

The `question` and `failed` argument checks happen inside the `case` statement, which runs **after** port-file read and `git branch --show-current`. This means:

1. If you call `harness-cli-for-agent.sh question` (missing arg) in an environment where the port file is absent, the user sees `"Harness server not running. Port file not found"` instead of `"question command requires a text argument"`. The real error is masked by an unrelated one. An agent receiving a confusing error wastes time diagnosing the wrong thing.

2. Every unknown-command call unnecessarily invokes git. The `test_unknown_command` and `test_missing_arguments` tests implicitly depend on the test environment being a git repository. If those tests ran in a non-git directory they would fail with the wrong error message.

**Fix:** Validate command and arity at the top of `main()` before any I/O:

```bash
main() {
  # Validate command and arity before touching port file or git
  case "$1" in
    done|status) ;;
    question)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: question command requires a text argument" >&2; exit 1
      fi ;;
    failed)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: failed command requires a reason argument" >&2; exit 1
      fi ;;
    *)
      echo "ERROR: Unknown command [$1]" >&2; show_help >&2; exit 1 ;;
  esac

  local PORT
  PORT=$(_read_port)
  local BRANCH
  BRANCH=$(_get_branch)
  ...
```

---

## Suggestions

### [MINOR] Port validation accepts `0` as valid

**File:** `scripts/harness-cli-for-agent.sh`, line 27

```bash
if [[ ! "${port}" =~ ^[0-9]+$ ]]; then
```

This accepts `0`, `00`, `00000`, etc. Port 0 is not a valid listening port (the OS uses it to mean "assign any port"). The harness writes the OS-assigned port after binding, which is always >= 1. The regex could be tightened:

```bash
if [[ ! "${port}" =~ ^[1-9][0-9]*$ ]] || [[ "${port}" -gt 65535 ]]; then
```

This is low risk because the harness will never write port 0, but it's a latent trap if the port file is manually edited or corrupted.

### [MINOR] curl HTTP error body is silently dropped

**File:** `scripts/harness-cli-for-agent.sh`, line 61–65

```bash
curl --silent --fail --show-error \
  -X POST \
  -H "Content-Type: application/json" \
  -d "${json_body}" \
  "${url}"
```

When the harness returns a 4xx (e.g., unknown branch), `--fail` causes curl to exit non-zero and `--show-error` prints `"curl: (22) The requested URL returned error: 400"` to stderr. The error body from the harness (which may contain a meaningful message like `"unknown branch: feature-x"`) is silently discarded.

For `question`, `done`, and `failed` this means the agent has no diagnostic information when the harness rejects its request. Consider adding `--fail-with-body` (curl >= 7.76) to preserve the response body on error, or remove `--fail` and handle non-200 status explicitly.

### [MINOR] No test verifies the branch VALUE reaches the JSON body

**File:** `scripts/test_harness_cli.sh`, lines 206, 262

```bash
assert_contains "${CAPTURED_STDOUT}" '"branch"' "THEN: JSON body contains branch field"
```

The test checks that the key `"branch"` is present in the JSON body but does not assert the value. The harness uses the branch as the primary routing key for all requests. A test that verifies the actual current branch name ends up in the JSON would give stronger confidence. This is low priority since `jq --arg` correctly maps the variable.

---

## Summary

The script is well-structured and handles the happy path correctly. jq is used properly for safe JSON construction (no injection risk). Special characters are handled correctly. The `read -r || true` fix for no-trailing-newline port files is correct.

The two MAJOR issues both stem from the same root: the `main()` function does too much in a fixed order, conflating I/O operations (port read, git invocation) with pure validation (argument presence check). Fixing argument validation ordering also resolves the hidden-contract issue with `_post()` if PORT is moved to a parameter. These are straightforward fixes that would make the script significantly more robust and self-documenting.
