#!/usr/bin/env bash
# callback_shepherd.signal.sh — Agent-to-harness callback script.
# Posts fire-and-forget signals to the Shepherd server via HTTP.
#
# Usage:
#   callback_shepherd.signal.sh started
#   callback_shepherd.signal.sh done <result>                 # completed | pass | needs_iteration
#   callback_shepherd.signal.sh user-question "<text>"
#   callback_shepherd.signal.sh fail-workflow "<reason>"
#   callback_shepherd.signal.sh ack-payload <payload-id>
#   callback_shepherd.signal.sh self-compacted
#
# Environment:
#   TICKET_SHEPHERD_SERVER_PORT   — required, server port
#   TICKET_SHEPHERD_HANDSHAKE_GUID — required, agent identity
#
# Retry policy (ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E):
#   3 total attempts, backoff 1s then 5s.
#   Transient: connection refused, reset, timeout, HTTP 5xx → retry.
#   Non-transient: HTTP 400, 404 → fail immediately.

set -euo pipefail

# ---------------------------------------------------------------------------
# Fail-fast: required environment variables
# ---------------------------------------------------------------------------

if [[ -z "${TICKET_SHEPHERD_SERVER_PORT:-}" ]]; then
  echo "ERROR: TICKET_SHEPHERD_SERVER_PORT is not set" >&2
  exit 1
fi

if [[ -z "${TICKET_SHEPHERD_HANDSHAKE_GUID:-}" ]]; then
  echo "ERROR: TICKET_SHEPHERD_HANDSHAKE_GUID is not set" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Action argument
# ---------------------------------------------------------------------------

if [[ $# -lt 1 ]]; then
  echo "ERROR: missing action argument. Usage: callback_shepherd.signal.sh <action> [args...]" >&2
  exit 1
fi

ACTION="$1"
shift

# ---------------------------------------------------------------------------
# Build JSON payload per action
# ---------------------------------------------------------------------------

GUID="${TICKET_SHEPHERD_HANDSHAKE_GUID}"

# [json_escape]: escapes backslashes, double quotes, and control characters
# so that arbitrary user text can be safely embedded in a JSON string value.
json_escape() {
  local input="$1"
  # Escape backslashes first, then double quotes, then newlines/tabs/carriage returns
  input="${input//\\/\\\\}"
  input="${input//\"/\\\"}"
  input="${input//$'\n'/\\n}"
  input="${input//$'\t'/\\t}"
  input="${input//$'\r'/\\r}"
  echo -n "$input"
}

case "${ACTION}" in
  started)
    JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\"}"
    ;;

  done)
    if [[ $# -ne 1 ]]; then
      echo "ERROR: 'done' action requires exactly one argument: completed | pass | needs_iteration" >&2
      exit 1
    fi
    RESULT="$1"
    case "${RESULT}" in
      completed|pass|needs_iteration)
        ;;
      *)
        echo "ERROR: invalid done result=[${RESULT}]. Must be one of: completed, pass, needs_iteration" >&2
        exit 1
        ;;
    esac
    JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\",\"result\":\"${RESULT}\"}"
    ;;

  user-question)
    if [[ $# -ne 1 ]]; then
      echo "ERROR: 'user-question' action requires exactly one argument: the question text" >&2
      exit 1
    fi
    ESCAPED_QUESTION="$(json_escape "$1")"
    JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\",\"question\":\"${ESCAPED_QUESTION}\"}"
    ;;

  fail-workflow)
    if [[ $# -ne 1 ]]; then
      echo "ERROR: 'fail-workflow' action requires exactly one argument: the failure reason" >&2
      exit 1
    fi
    ESCAPED_REASON="$(json_escape "$1")"
    JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\",\"reason\":\"${ESCAPED_REASON}\"}"
    ;;

  ack-payload)
    if [[ $# -ne 1 ]]; then
      echo "ERROR: 'ack-payload' action requires exactly one argument: the payload ID" >&2
      exit 1
    fi
    PAYLOAD_ID="$1"
    if [[ -z "${PAYLOAD_ID}" ]]; then
      echo "ERROR: 'ack-payload' payload ID must not be empty" >&2
      exit 1
    fi
    JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\",\"payloadId\":\"${PAYLOAD_ID}\"}"
    ;;

  self-compacted)
    JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\"}"
    ;;

  *)
    echo "ERROR: unknown action=[${ACTION}]. Valid actions: started, done, user-question, fail-workflow, ack-payload, self-compacted" >&2
    exit 1
    ;;
esac

# ---------------------------------------------------------------------------
# HTTP POST with retry on transient failures
# ---------------------------------------------------------------------------

URL="http://localhost:${TICKET_SHEPHERD_SERVER_PORT}/callback-shepherd/signal/${ACTION}"

MAX_ATTEMPTS=3
BACKOFF_SECONDS=(0 1 5)  # index 0 unused; backoff after attempt 1 = 1s, after attempt 2 = 5s

for (( attempt=1; attempt<=MAX_ATTEMPTS; attempt++ )); do
  HTTP_CODE=""
  CURL_EXIT=0

  # [curl flags]:
  #   --silent --show-error: suppress progress but show errors
  #   --output /dev/null: discard response body (fire-and-forget)
  #   --write-out '%{http_code}': capture HTTP status code
  #   --max-time 30: timeout after 30 seconds
  HTTP_CODE=$(curl \
    --silent \
    --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    --max-time 30 \
    --header "Content-Type: application/json" \
    --data "${JSON_PAYLOAD}" \
    --request POST \
    "${URL}" 2>/dev/null) || CURL_EXIT=$?

  # -- Success --
  if [[ "${CURL_EXIT}" -eq 0 && "${HTTP_CODE}" == "200" ]]; then
    exit 0
  fi

  # -- Non-transient HTTP errors: fail immediately --
  if [[ "${CURL_EXIT}" -eq 0 ]]; then
    HTTP_CODE_INT="${HTTP_CODE}"
    if [[ "${HTTP_CODE_INT}" == "400" || "${HTTP_CODE_INT}" == "404" ]]; then
      echo "ERROR: server returned HTTP ${HTTP_CODE_INT} for action=[${ACTION}] (non-transient, not retrying)" >&2
      exit 1
    fi
  fi

  # -- Transient failure: curl error or HTTP 5xx → retry --
  if (( attempt < MAX_ATTEMPTS )); then
    BACKOFF="${BACKOFF_SECONDS[$attempt]}"
    echo "WARN: attempt ${attempt}/${MAX_ATTEMPTS} failed for action=[${ACTION}] (curl_exit=[${CURL_EXIT}], http_code=[${HTTP_CODE:-N/A}]). Retrying in ${BACKOFF}s..." >&2
    sleep "${BACKOFF}"
  fi
done

echo "ERROR: all ${MAX_ATTEMPTS} attempts failed for action=[${ACTION}] (last curl_exit=[${CURL_EXIT}], last http_code=[${HTTP_CODE:-N/A}])" >&2
exit 1
