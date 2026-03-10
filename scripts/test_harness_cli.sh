#!/usr/bin/env bash
# Test script for harness-cli-for-agent.sh
#
# [set -euo pipefail]: Used directly (not __enable_bash_strict_mode__) because
# this script runs standalone without thorg shell env.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HARNESS_CLI="${SCRIPT_DIR}/harness-cli-for-agent.sh"

PASS_COUNT=0
FAIL_COUNT=0

# --- Assertion Helpers ---

assert_equals() {
  local expected="$1"
  local actual="$2"
  local description="$3"

  if [[ "${expected}" == "${actual}" ]]; then
    echo "  PASS: ${description}"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL: ${description}"
    echo "    expected=[${expected}]"
    echo "    actual  =[${actual}]"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local description="$3"

  if [[ "${haystack}" == *"${needle}"* ]]; then
    echo "  PASS: ${description}"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL: ${description}"
    echo "    haystack does not contain [${needle}]"
    echo "    haystack=[${haystack}]"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# Runs a command and captures stdout, stderr, and exit code.
# Results are stored in CAPTURED_STDOUT, CAPTURED_STDERR, CAPTURED_EXIT_CODE.
run_capturing() {
  local stderr_file
  stderr_file=$(mktemp)

  CAPTURED_EXIT_CODE=0
  CAPTURED_STDOUT=$("$@" 2>"${stderr_file}") || CAPTURED_EXIT_CODE=$?
  CAPTURED_STDERR=$(< "${stderr_file}")
  rm -f "${stderr_file}"
}

# --- Shared Setup Helpers ---

# Creates a temporary HOME directory without a port file.
# Sets HOME to the temp dir. The caller is responsible for cleanup.
setup_temp_home() {
  TEMP_HOME=$(mktemp -d)
  export HOME="${TEMP_HOME}"
}

# Creates a temporary HOME directory WITH a valid port file (port=12345).
# Sets HOME to the temp dir. The caller is responsible for cleanup.
setup_temp_home_with_port() {
  TEMP_HOME=$(mktemp -d)
  export HOME="${TEMP_HOME}"
  mkdir -p "${TEMP_HOME}/.chainsaw_agent_harness/server"
  echo "12345" > "${TEMP_HOME}/.chainsaw_agent_harness/server/port.txt"
}

# --- Test Categories ---

test_help_output() {
  echo "GIVEN: --help flag"

  run_capturing "${HARNESS_CLI}" --help
  echo "  WHEN: --help is passed"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: exits 0"
  assert_contains "${CAPTURED_STDOUT}" "Usage:" "THEN: output contains 'Usage:'"
  assert_contains "${CAPTURED_STDOUT}" "done" "THEN: output contains 'done' command"
  assert_contains "${CAPTURED_STDOUT}" "question" "THEN: output contains 'question' command"
  assert_contains "${CAPTURED_STDOUT}" "failed" "THEN: output contains 'failed' command"
  assert_contains "${CAPTURED_STDOUT}" "status" "THEN: output contains 'status' command"

  run_capturing "${HARNESS_CLI}" -h
  echo "  WHEN: -h is passed"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: -h exits 0"
  assert_contains "${CAPTURED_STDOUT}" "Usage:" "THEN: -h output contains 'Usage:'"

  run_capturing "${HARNESS_CLI}"
  echo "  WHEN: no arguments"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: no args exits 0"
  assert_contains "${CAPTURED_STDOUT}" "Usage:" "THEN: no args shows help"
}

test_port_file_missing() {
  echo "GIVEN: port file does not exist"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home

  run_capturing "${HARNESS_CLI}" done
  echo "  WHEN: 'done' command is run"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "not found" "THEN: stderr mentions 'not found'"
  assert_contains "${CAPTURED_STDERR}" "Port file" "THEN: stderr mentions 'Port file'"

  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_port_file_no_trailing_newline() {
  echo "GIVEN: port file exists but has no trailing newline"

  local ORIGINAL_HOME="${HOME}"
  TEMP_HOME=$(mktemp -d)
  export HOME="${TEMP_HOME}"
  mkdir -p "${TEMP_HOME}/.chainsaw_agent_harness/server"
  # [printf]: writes port WITHOUT trailing newline (unlike echo)
  printf "12345" > "${TEMP_HOME}/.chainsaw_agent_harness/server/port.txt"
  export HARNESS_CLI_DRY_RUN=true

  run_capturing "${HARNESS_CLI}" done
  echo "  WHEN: 'done' is run with no-newline port file"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: exits 0 (read -r does not cause silent failure)"
  assert_contains "${CAPTURED_STDOUT}" "URL=http://localhost:12345/agent/done" "THEN: port is correctly read"

  unset HARNESS_CLI_DRY_RUN
  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_invalid_port_value() {
  echo "GIVEN: port file contains a non-numeric value"

  local ORIGINAL_HOME="${HOME}"
  TEMP_HOME=$(mktemp -d)
  export HOME="${TEMP_HOME}"
  mkdir -p "${TEMP_HOME}/.chainsaw_agent_harness/server"
  echo "abc" > "${TEMP_HOME}/.chainsaw_agent_harness/server/port.txt"

  run_capturing "${HARNESS_CLI}" done
  echo "  WHEN: 'done' command is run"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "Invalid port value" "THEN: stderr mentions 'Invalid port value'"
  assert_contains "${CAPTURED_STDERR}" "abc" "THEN: stderr includes the bad value"

  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_missing_arguments() {
  echo "GIVEN: commands that require arguments (no port file needed -- validation is before I/O)"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home

  run_capturing "${HARNESS_CLI}" question
  echo "  WHEN: 'question' without text argument"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "requires a text argument" "THEN: stderr explains missing argument"

  run_capturing "${HARNESS_CLI}" failed
  echo "  WHEN: 'failed' without reason argument"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "requires a reason argument" "THEN: stderr explains missing argument"

  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_unknown_command() {
  echo "GIVEN: unknown subcommand (no port file needed -- validation is before I/O)"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home

  run_capturing "${HARNESS_CLI}" bogus
  echo "  WHEN: 'bogus' command is run"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "Unknown command" "THEN: stderr mentions 'Unknown command'"
  assert_contains "${CAPTURED_STDERR}" "bogus" "THEN: stderr includes the bad command name"

  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_dry_run_done() {
  echo "GIVEN: DRY_RUN mode with 'done' command"

  local ORIGINAL_HOME="${HOME}"
  local EXPECTED_BRANCH
  EXPECTED_BRANCH=$(git branch --show-current)
  setup_temp_home_with_port
  export HARNESS_CLI_DRY_RUN=true

  run_capturing "${HARNESS_CLI}" done
  echo "  WHEN: 'done' is run in DRY_RUN mode"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: exits 0"
  assert_contains "${CAPTURED_STDOUT}" "URL=http://localhost:12345/agent/done" "THEN: URL is correct"
  assert_contains "${CAPTURED_STDOUT}" '"branch"' "THEN: JSON body contains branch field"
  assert_contains "${CAPTURED_STDOUT}" "${EXPECTED_BRANCH}" "THEN: JSON body contains current branch value"

  unset HARNESS_CLI_DRY_RUN
  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_dry_run_question() {
  echo "GIVEN: DRY_RUN mode with 'question' command"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home_with_port
  export HARNESS_CLI_DRY_RUN=true

  run_capturing "${HARNESS_CLI}" question "How should I proceed?"
  echo "  WHEN: 'question' is run with text"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: exits 0"
  assert_contains "${CAPTURED_STDOUT}" "URL=http://localhost:12345/agent/question" "THEN: URL is correct"
  assert_contains "${CAPTURED_STDOUT}" '"question"' "THEN: JSON body contains question field"
  assert_contains "${CAPTURED_STDOUT}" "How should I proceed?" "THEN: JSON body contains the question text"

  unset HARNESS_CLI_DRY_RUN
  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_dry_run_failed() {
  echo "GIVEN: DRY_RUN mode with 'failed' command"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home_with_port
  export HARNESS_CLI_DRY_RUN=true

  run_capturing "${HARNESS_CLI}" failed "Cannot compile"
  echo "  WHEN: 'failed' is run with reason"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: exits 0"
  assert_contains "${CAPTURED_STDOUT}" "URL=http://localhost:12345/agent/failed" "THEN: URL is correct"
  assert_contains "${CAPTURED_STDOUT}" '"reason"' "THEN: JSON body contains reason field"
  assert_contains "${CAPTURED_STDOUT}" "Cannot compile" "THEN: JSON body contains the reason text"

  unset HARNESS_CLI_DRY_RUN
  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_dry_run_status() {
  echo "GIVEN: DRY_RUN mode with 'status' command"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home_with_port
  export HARNESS_CLI_DRY_RUN=true

  run_capturing "${HARNESS_CLI}" status
  echo "  WHEN: 'status' is run in DRY_RUN mode"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: exits 0"
  assert_contains "${CAPTURED_STDOUT}" "URL=http://localhost:12345/agent/status" "THEN: URL is correct"
  assert_contains "${CAPTURED_STDOUT}" '"branch"' "THEN: JSON body contains branch field"

  unset HARNESS_CLI_DRY_RUN
  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_dry_run_special_characters() {
  echo "GIVEN: DRY_RUN mode with special characters in question text"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home_with_port
  export HARNESS_CLI_DRY_RUN=true

  run_capturing "${HARNESS_CLI}" question 'How to handle "special" chars & newlines?'
  echo "  WHEN: 'question' text contains quotes and ampersands"
  assert_equals "0" "${CAPTURED_EXIT_CODE}" "THEN: exits 0"
  assert_contains "${CAPTURED_STDOUT}" "URL=http://localhost:12345/agent/question" "THEN: URL is correct"
  # jq properly escapes the quotes in JSON
  assert_contains "${CAPTURED_STDOUT}" '\"special\"' "THEN: quotes are properly escaped in JSON"

  unset HARNESS_CLI_DRY_RUN
  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_port_value_zero() {
  echo "GIVEN: port file contains value 0"

  local ORIGINAL_HOME="${HOME}"
  TEMP_HOME=$(mktemp -d)
  export HOME="${TEMP_HOME}"
  mkdir -p "${TEMP_HOME}/.chainsaw_agent_harness/server"
  echo "0" > "${TEMP_HOME}/.chainsaw_agent_harness/server/port.txt"

  run_capturing "${HARNESS_CLI}" done
  echo "  WHEN: 'done' command is run"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "Invalid port value" "THEN: stderr rejects port 0"

  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_port_value_above_max() {
  echo "GIVEN: port file contains value above 65535"

  local ORIGINAL_HOME="${HOME}"
  TEMP_HOME=$(mktemp -d)
  export HOME="${TEMP_HOME}"
  mkdir -p "${TEMP_HOME}/.chainsaw_agent_harness/server"
  echo "65536" > "${TEMP_HOME}/.chainsaw_agent_harness/server/port.txt"

  run_capturing "${HARNESS_CLI}" done
  echo "  WHEN: 'done' command is run"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "Invalid port value" "THEN: stderr rejects port above max"

  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

test_arity_errors_before_io() {
  echo "GIVEN: commands requiring arguments, with NO port file present"

  local ORIGINAL_HOME="${HOME}"
  setup_temp_home

  run_capturing "${HARNESS_CLI}" question
  echo "  WHEN: 'question' without text argument (port file absent)"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "requires a text argument" "THEN: arity error shown, not port-file error"

  run_capturing "${HARNESS_CLI}" failed
  echo "  WHEN: 'failed' without reason argument (port file absent)"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "requires a reason argument" "THEN: arity error shown, not port-file error"

  run_capturing "${HARNESS_CLI}" bogus
  echo "  WHEN: unknown command (port file absent)"
  assert_equals "1" "${CAPTURED_EXIT_CODE}" "THEN: exits 1"
  assert_contains "${CAPTURED_STDERR}" "Unknown command" "THEN: command error shown, not port-file error"

  export HOME="${ORIGINAL_HOME}"
  rm -rf "${TEMP_HOME}"
}

# --- Run All Tests ---

echo "========================================="
echo "  harness-cli-for-agent.sh -- Test Suite"
echo "========================================="
echo ""

test_help_output
echo ""
test_port_file_missing
echo ""
test_port_file_no_trailing_newline
echo ""
test_invalid_port_value
echo ""
test_missing_arguments
echo ""
test_unknown_command
echo ""
test_dry_run_done
echo ""
test_dry_run_question
echo ""
test_dry_run_failed
echo ""
test_dry_run_status
echo ""
test_dry_run_special_characters
echo ""
test_port_value_zero
echo ""
test_port_value_above_max
echo ""
test_arity_errors_before_io
echo ""

# --- Summary ---

echo "========================================="
echo "  Results: ${PASS_COUNT} passed, ${FAIL_COUNT} failed"
echo "========================================="

if [[ ${FAIL_COUNT} -gt 0 ]]; then
  exit 1
fi

exit 0
