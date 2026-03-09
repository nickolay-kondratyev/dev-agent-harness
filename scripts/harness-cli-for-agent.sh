#!/usr/bin/env bash
# ap.8PB8nMd93D3jipEWhME5n.E -- Agent-to-Harness CLI script.
# Agents running in TMUX sessions use this script to communicate back to
# the Chainsaw harness via HTTP POST requests.
#
# [set -euo pipefail]: Used directly (not __enable_bash_strict_mode__) because
# this script runs standalone on agent PATH without thorg shell env.
set -euo pipefail

# --- Constants ---
PORT_FILE="${HOME}/.chainsaw_agent_harness/server/port.txt"

# --- Helper Functions ---

_read_port() {
  if [[ ! -f "${PORT_FILE}" ]]; then
    echo "ERROR: Harness server not running. Port file not found at [${PORT_FILE}]" >&2
    exit 1
  fi

  local port
  # [read -r]: naturally strips trailing newline from port file
  read -r port < "${PORT_FILE}"

  if [[ ! "${port}" =~ ^[0-9]+$ ]]; then
    echo "ERROR: Invalid port value [${port}] in [${PORT_FILE}]" >&2
    exit 1
  fi

  echo "${port}"
}

_get_branch() {
  local branch
  branch=$(git branch --show-current 2>/dev/null) || {
    echo "ERROR: Failed to determine git branch. Are you in a git repository?" >&2
    exit 1
  }

  if [[ -z "${branch}" ]]; then
    echo "ERROR: Cannot determine git branch. Detached HEAD state is not supported." >&2
    exit 1
  fi

  echo "${branch}"
}

_post() {
  local endpoint="$1"
  local json_body="$2"
  local url="http://localhost:${PORT}/agent/${endpoint}"

  if [[ "${HARNESS_CLI_DRY_RUN:-}" == "true" ]]; then
    echo "URL=${url}"
    echo "BODY=${json_body}"
    return 0
  fi

  curl --silent --fail --show-error \
    -X POST \
    -H "Content-Type: application/json" \
    -d "${json_body}" \
    "${url}"
}

show_help() {
  cat <<'EOF'
harness-cli-for-agent.sh -- Agent-to-Harness CLI

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
EOF
}

main() {
  if [[ $# -eq 0 ]] || [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
    show_help
    exit 0
  fi

  local PORT
  PORT=$(_read_port)

  local BRANCH
  BRANCH=$(_get_branch)

  case "$1" in
    done)
      local json_body
      json_body=$(jq -n --arg branch "${BRANCH}" '{branch: $branch}')
      _post "done" "${json_body}"
      ;;
    question)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: question command requires a text argument" >&2
        exit 1
      fi
      local json_body
      json_body=$(jq -n --arg branch "${BRANCH}" --arg question "$2" \
        '{branch: $branch, question: $question}')
      _post "question" "${json_body}"
      ;;
    failed)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: failed command requires a reason argument" >&2
        exit 1
      fi
      local json_body
      json_body=$(jq -n --arg branch "${BRANCH}" --arg reason "$2" \
        '{branch: $branch, reason: $reason}')
      _post "failed" "${json_body}"
      ;;
    status)
      local json_body
      json_body=$(jq -n --arg branch "${BRANCH}" '{branch: $branch}')
      _post "status" "${json_body}"
      ;;
    *)
      echo "ERROR: Unknown command [$1]" >&2
      show_help >&2
      exit 1
      ;;
  esac
}

main "${@}"
