# Implementation Review - Private Notes

## Review Date: 2026-03-19

## Files Reviewed
- `app/src/main/resources/scripts/callback_shepherd.signal.sh`
- `app/src/test/bash/test_callback_shepherd_signal.sh`
- `doc/core/agent-to-server-communication-protocol.md` (spec)

## Test Results
- 22/22 bash tests pass
- sanity_check.sh fails due to environment issue (git hook syntax error in claude_core_slim_planning.sh), NOT related to this change

## Key Findings

### IMPORTANT: curl stderr swallowed (line 159)
The `2>/dev/null` on the curl command silences all curl error messages. This means:
- When retry WARN messages fire, the actual curl error (e.g., "Connection refused") is lost
- After all retries fail, the final ERROR message only has curl_exit code, not the human-readable reason
- This makes debugging harder in the field

The `--show-error` flag is included but its output goes to /dev/null.

### IMPORTANT: GUID not JSON-escaped (line 71, 88, 119, 123)
The TICKET_SHEPHERD_HANDSHAKE_GUID is embedded directly into JSON without escaping. While the format is
`handshake.${UUID}` (alphanumeric + dots + hyphens, safe for JSON), this is an implicit contract.
If the GUID format ever changes to include quotes or backslashes, this breaks silently.
The json_escape function exists but is only used for user-question and fail-workflow.

### IMPORTANT: payloadId not JSON-escaped (line 119)
Same issue as GUID — PAYLOAD_ID is embedded raw. Format is `{guid_short}-{N}` which is safe today,
but the json_escape function is available and would add safety.

### Suggestion: HTTP success should accept 2xx range, not just 200
The spec says "bare 200 OK" so checking for exactly 200 is correct per spec. However, if the server
ever returns 201 or 204 (common HTTP success codes), the script would retry and fail. This is a minor
concern given the spec is explicit.

### Suggestion: No test for ack-payload with empty string argument
There's a validation check for empty PAYLOAD_ID (line 115-118) but no test covering this case.
The `run_script_env` helper passes arguments through, so testing empty string would verify this path.

### Positive Observations
- Retry logic correctly handles transient vs non-transient
- JSON escaping covers the important control characters
- Backoff array indexing is correct (attempt 1 -> index 1 = 1s, attempt 2 -> index 2 = 5s)
- `set -euo pipefail` is good defensive scripting
- Error messages follow `key=[value]` logging convention from CLAUDE.md
- Tests use `env -i` for clean environment isolation — thorough
- Mock curl approach avoids real HTTP calls — tests are fast and reliable
