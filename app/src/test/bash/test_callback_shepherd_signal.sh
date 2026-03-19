#!/usr/bin/env bash
# Unit tests for callback_shepherd.signal.sh
#
# Tests argument validation and env-var checks WITHOUT requiring a running server.
# curl is mocked to avoid actual HTTP calls.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
SCRIPT_UNDER_TEST="${REPO_ROOT}/app/src/main/resources/scripts/callback_shepherd.signal.sh"

# ---------------------------------------------------------------------------
# Minimal test framework
# ---------------------------------------------------------------------------

TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0
FAILED_TESTS=()

pass() {
  ((TESTS_PASSED++)) || true
  echo "  PASS: $1"
}

fail() {
  ((TESTS_FAILED++)) || true
  FAILED_TESTS+=("$1")
  echo "  FAIL: $1"
}

# ---------------------------------------------------------------------------
# Helper: run the script capturing exit code and stderr
# Uses a temp file to avoid subshell variable scoping issues.
# ---------------------------------------------------------------------------

# [TMPDIR override]: /dev/shm is often mounted noexec in sandboxed environments,
# which prevents mock scripts from executing. Use repo .tmp/ instead.
TMPDIR_TEST="$(mktemp -d "${REPO_ROOT}/.tmp/test_signal_XXXXXX")"
trap 'rm -rf "${TMPDIR_TEST}"' EXIT

run_script_env() {
  # Usage: run_script_env <port> <guid> <args...>
  # Pass empty string for port/guid to unset them.
  local port="$1"; shift
  local guid="$1"; shift

  local stderr_file="${TMPDIR_TEST}/stderr.txt"

  # Build env args — only set vars that have values (empty string means unset)
  local env_args=()
  if [[ -n "${port}" ]]; then
    env_args+=("TICKET_SHEPHERD_SERVER_PORT=${port}")
  fi
  if [[ -n "${guid}" ]]; then
    env_args+=("TICKET_SHEPHERD_HANDSHAKE_GUID=${guid}")
  fi

  LAST_EXIT_CODE=0
  # [env -i]: start with clean environment so unset vars are truly absent
  env -i PATH="/usr/bin:/bin:/usr/sbin:/sbin" "${env_args[@]}" \
    bash --norc --noprofile "${SCRIPT_UNDER_TEST}" "$@" >/dev/null 2>"${stderr_file}" || LAST_EXIT_CODE=$?
  LAST_STDERR="$(cat "${stderr_file}")"
}

# Like run_script_env but prepends mock curl to PATH
run_script_env_with_mock() {
  local port="$1"; shift
  local guid="$1"; shift
  local mock_dir="$1"; shift

  local stderr_file="${TMPDIR_TEST}/stderr.txt"

  LAST_EXIT_CODE=0
  # Use env to set vars cleanly; --norc --noprofile prevents profile scripts from reordering PATH
  env \
    TICKET_SHEPHERD_SERVER_PORT="${port}" \
    TICKET_SHEPHERD_HANDSHAKE_GUID="${guid}" \
    PATH="${mock_dir}:${PATH}" \
    bash --norc --noprofile "${SCRIPT_UNDER_TEST}" "$@" >/dev/null 2>"${stderr_file}" || LAST_EXIT_CODE=$?
  LAST_STDERR="$(cat "${stderr_file}")"
}

# ---------------------------------------------------------------------------
# Mock curl helpers
# ---------------------------------------------------------------------------

create_mock_curl_200() {
  local dir="$(mktemp -d "${TMPDIR_TEST}/mock_XXXXXX")"
  cat > "${dir}/curl" << 'EOF'
#!/usr/bin/env bash
echo -n "200"
exit 0
EOF
  chmod +x "${dir}/curl"
  echo "${dir}"
}

create_mock_curl_with_code() {
  local http_code="$1"
  local dir="$(mktemp -d "${TMPDIR_TEST}/mock_XXXXXX")"
  cat > "${dir}/curl" << EOF
#!/usr/bin/env bash
echo -n "${http_code}"
exit 0
EOF
  chmod +x "${dir}/curl"
  echo "${dir}"
}

create_mock_curl_connection_refused() {
  local dir="$(mktemp -d "${TMPDIR_TEST}/mock_XXXXXX")"
  cat > "${dir}/curl" << 'EOF'
#!/usr/bin/env bash
exit 7
EOF
  chmod +x "${dir}/curl"
  echo "${dir}"
}

# ---------------------------------------------------------------------------
# Tests: Missing environment variables
# ---------------------------------------------------------------------------

echo "=========================================="
echo "Testing: callback_shepherd.signal.sh"
echo "=========================================="
echo ""

echo "--- test: missing TICKET_SHEPHERD_SERVER_PORT"
((TESTS_RUN++)) || true
run_script_env "" "handshake.test-guid" started
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"TICKET_SHEPHERD_SERVER_PORT"* ]]; then
  pass "missing TICKET_SHEPHERD_SERVER_PORT exits non-zero with clear error"
else
  fail "missing TICKET_SHEPHERD_SERVER_PORT: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: missing TICKET_SHEPHERD_HANDSHAKE_GUID"
((TESTS_RUN++)) || true
run_script_env "8080" "" started
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"TICKET_SHEPHERD_HANDSHAKE_GUID"* ]]; then
  pass "missing TICKET_SHEPHERD_HANDSHAKE_GUID exits non-zero with clear error"
else
  fail "missing TICKET_SHEPHERD_HANDSHAKE_GUID: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: no action argument"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid"
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"missing action"* ]]; then
  pass "no action argument exits non-zero with clear error"
else
  fail "no action argument: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: unknown action"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" banana
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"unknown action"* ]]; then
  pass "unknown action exits non-zero with clear error"
else
  fail "unknown action: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

# ---------------------------------------------------------------------------
# Tests: 'done' action validation
# ---------------------------------------------------------------------------

echo "--- test: 'done' missing result"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" done
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"requires exactly one argument"* ]]; then
  pass "'done' without result exits non-zero"
else
  fail "'done' without result: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: 'done' invalid result"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" done "invalid_result"
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"invalid done result"* ]]; then
  pass "'done' with invalid result exits non-zero"
else
  fail "'done' with invalid result: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: 'done completed' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" done completed
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'done completed' succeeds with mock curl"
else
  fail "'done completed': exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

echo "--- test: 'done pass' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" done pass
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'done pass' succeeds with mock curl"
else
  fail "'done pass': exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

echo "--- test: 'done needs_iteration' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" done needs_iteration
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'done needs_iteration' succeeds with mock curl"
else
  fail "'done needs_iteration': exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

# ---------------------------------------------------------------------------
# Tests: 'ack-payload' action validation
# ---------------------------------------------------------------------------

echo "--- test: 'ack-payload' missing arg"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" ack-payload
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"requires exactly one argument"* ]]; then
  pass "'ack-payload' without argument exits non-zero"
else
  fail "'ack-payload' without argument: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: 'ack-payload' empty string arg"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" ack-payload ""
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"must not be empty"* ]]; then
  pass "'ack-payload' with empty string exits non-zero"
else
  fail "'ack-payload' with empty string: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: 'ack-payload' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" ack-payload "a1b2c3d4-3"
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'ack-payload a1b2c3d4-3' succeeds with mock curl"
else
  fail "'ack-payload a1b2c3d4-3': exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

# ---------------------------------------------------------------------------
# Tests: 'user-question' and 'fail-workflow'
# ---------------------------------------------------------------------------

echo "--- test: 'user-question' missing text"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" user-question
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"requires exactly one argument"* ]]; then
  pass "'user-question' without text exits non-zero"
else
  fail "'user-question' without text: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: 'user-question' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" user-question "How should I handle X?"
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'user-question' with text succeeds with mock curl"
else
  fail "'user-question' with text: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

echo "--- test: 'fail-workflow' missing reason"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" fail-workflow
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"requires exactly one argument"* ]]; then
  pass "'fail-workflow' without reason exits non-zero"
else
  fail "'fail-workflow' without reason: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi

echo "--- test: 'fail-workflow' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" fail-workflow "Cannot compile after multiple approaches"
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'fail-workflow' with reason succeeds with mock curl"
else
  fail "'fail-workflow' with reason: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

# ---------------------------------------------------------------------------
# Tests: 'started' and 'self-compacted' (no extra args)
# ---------------------------------------------------------------------------

echo "--- test: 'started' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" started
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'started' succeeds with mock curl"
else
  fail "'started': exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

echo "--- test: 'self-compacted' valid"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_200)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" self-compacted
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  pass "'self-compacted' succeeds with mock curl"
else
  fail "'self-compacted': exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

# ---------------------------------------------------------------------------
# Tests: Non-transient HTTP errors (no retry)
# ---------------------------------------------------------------------------

echo "--- test: HTTP 400 no retry"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_with_code 400)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" started
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"non-transient"* ]]; then
  pass "HTTP 400 fails immediately without retry"
else
  fail "HTTP 400: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

echo "--- test: HTTP 404 no retry"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_with_code 404)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" started
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"non-transient"* ]]; then
  pass "HTTP 404 fails immediately without retry"
else
  fail "HTTP 404: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

# ---------------------------------------------------------------------------
# Tests: Transient failures retry then fail
# Note: These tests use instant-fail mocks (no real sleep). The retry backoffs
# (1s, 5s) make these tests take ~6s total.
# ---------------------------------------------------------------------------

echo "--- test: connection refused retries then fails (takes ~6s)"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_connection_refused)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" started
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"all 3 attempts failed"* ]]; then
  pass "connection refused retries then fails after max attempts"
else
  fail "connection refused: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

echo "--- test: HTTP 500 retries then fails (takes ~6s)"
((TESTS_RUN++)) || true
MOCK_DIR="$(create_mock_curl_with_code 500)"
run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" started
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"all 3 attempts failed"* ]]; then
  pass "HTTP 500 retries then fails after max attempts"
else
  fail "HTTP 500: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

# ---------------------------------------------------------------------------
# Tests: JSON escaping for special characters
# ---------------------------------------------------------------------------

echo "--- test: user-question with quotes gets proper JSON escaping"
((TESTS_RUN++)) || true
MOCK_DIR="$(mktemp -d "${TMPDIR_TEST}/mock_XXXXXX")"
CAPTURE_FILE="${MOCK_DIR}/captured_data.txt"
cat > "${MOCK_DIR}/curl" << MOCK_EOF
#!/usr/bin/env bash
# Capture the --data value
while [[ \$# -gt 0 ]]; do
  case "\$1" in
    --data)
      echo "\$2" > "${CAPTURE_FILE}"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
echo -n "200"
exit 0
MOCK_EOF
chmod +x "${MOCK_DIR}/curl"

run_script_env_with_mock "8080" "handshake.test-guid" "${MOCK_DIR}" user-question 'How should I handle "special" chars?'
if [[ "${LAST_EXIT_CODE}" -eq 0 ]]; then
  if [[ -f "${CAPTURE_FILE}" ]]; then
    CAPTURED_DATA="$(cat "${CAPTURE_FILE}")"
    # The JSON should contain escaped quotes: \"special\"
    if [[ "${CAPTURED_DATA}" == *'\"special\"'* ]]; then
      pass "user-question with quotes has proper JSON escaping in payload"
    else
      fail "user-question JSON escaping: captured_data=[${CAPTURED_DATA}]"
    fi
  else
    fail "user-question JSON escaping: capture file not created"
  fi
else
  fail "user-question with quotes: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
rm -rf "${MOCK_DIR}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo ""
echo "=========================================="
echo "Results: ${TESTS_PASSED}/${TESTS_RUN} passed, ${TESTS_FAILED} failed"
echo "=========================================="

if [[ ${TESTS_FAILED} -gt 0 ]]; then
  echo ""
  echo "Failed tests:"
  for t in "${FAILED_TESTS[@]}"; do
    echo "  - ${t}"
  done
  exit 1
fi

exit 0
