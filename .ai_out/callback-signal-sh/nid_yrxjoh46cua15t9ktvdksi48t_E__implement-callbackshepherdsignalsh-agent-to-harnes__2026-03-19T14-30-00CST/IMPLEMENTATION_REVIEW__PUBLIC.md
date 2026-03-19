# Implementation Review: callback_shepherd.signal.sh

## Summary

The implementation of `callback_shepherd.signal.sh` is solid and faithfully implements the spec
(ref.ap.wLpW8YbvqpRdxDplnN7Vh.E). All 6 actions are present, env-var fail-fast works, retry logic
is correct with proper transient/non-transient classification, and the test suite (22/22 passing)
provides good coverage. Two important issues found, plus minor suggestions.

**Overall: APPROVE with requested changes on the two IMPORTANT items.**

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. curl stderr swallowed — debugging in production will be painful

**File:** `app/src/main/resources/scripts/callback_shepherd.signal.sh`, line 159

```bash
"${URL}" 2>/dev/null) || CURL_EXIT=$?
```

The `2>/dev/null` discards all curl error output. The script includes `--show-error` (line 152)
which tells curl to print error messages to stderr, but then pipes stderr to `/dev/null`.

**Impact:** When the script retries and eventually fails, the WARN and ERROR messages include the
curl exit code (e.g., `curl_exit=[7]`) but NOT the human-readable reason (e.g., "Failed to connect
to localhost port 8080: Connection refused"). In a production debugging scenario, an operator sees
`curl_exit=[7]` and has to look up what exit code 7 means.

**Suggested fix:** Capture curl stderr to a variable and include it in the WARN/ERROR messages:

```bash
CURL_STDERR_FILE="${TMPDIR:-/tmp}/callback_shepherd_curl_$$"
HTTP_CODE=$(curl \
  --silent \
  --show-error \
  --output /dev/null \
  --write-out '%{http_code}' \
  --max-time 30 \
  --header "Content-Type: application/json" \
  --data "${JSON_PAYLOAD}" \
  --request POST \
  "${URL}" 2>"${CURL_STDERR_FILE}") || CURL_EXIT=$?
CURL_STDERR="$(cat "${CURL_STDERR_FILE}" 2>/dev/null || true)"
rm -f "${CURL_STDERR_FILE}"
```

Then include `curl_stderr=[${CURL_STDERR}]` in the WARN/ERROR log lines.

### 2. GUID and payloadId not JSON-escaped

**File:** `app/src/main/resources/scripts/callback_shepherd.signal.sh`, lines 71, 88, 119, 123

The `TICKET_SHEPHERD_HANDSHAKE_GUID` and `PAYLOAD_ID` values are embedded directly into JSON strings
without using the `json_escape` function:

```bash
# Line 71 — GUID embedded raw
JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\"}"

# Line 119 — payloadId embedded raw
JSON_PAYLOAD="{\"handshakeGuid\":\"${GUID}\",\"payloadId\":\"${PAYLOAD_ID}\"}"
```

The `json_escape` function exists and is used for `user-question` and `fail-workflow`, but not for
these fields. While the current formats (`handshake.${UUID}` and `{guid_short}-{N}`) are safe,
applying `json_escape` consistently is a low-cost defensive measure that prevents a class of
future bugs if formats change.

**Suggested fix:** Use `json_escape` for all string values embedded in JSON:

```bash
ESCAPED_GUID="$(json_escape "${GUID}")"
# Then use ${ESCAPED_GUID} in all JSON_PAYLOAD constructions
```

---

## Suggestions

### 1. Missing test: ack-payload with empty string argument

The script validates against empty `PAYLOAD_ID` (lines 115-118) but no test covers this path.
Add a test:

```bash
echo "--- test: 'ack-payload' empty string arg"
((TESTS_RUN++)) || true
run_script_env "8080" "handshake.test-guid" ack-payload ""
if [[ "${LAST_EXIT_CODE}" -ne 0 && "${LAST_STDERR}" == *"must not be empty"* ]]; then
  pass "'ack-payload' with empty string exits non-zero"
else
  fail "'ack-payload' empty string: exit_code=[${LAST_EXIT_CODE}], stderr=[${LAST_STDERR}]"
fi
```

### 2. Consider accepting 2xx range (not just 200)

Line 162 checks `HTTP_CODE == "200"` exactly. The spec says "bare 200 OK" so this is correct per
spec. However, if server behavior ever changes to return 201 or 204 (both valid success codes),
the script would retry and fail. A more resilient check:

```bash
if [[ "${CURL_EXIT}" -eq 0 && "${HTTP_CODE}" =~ ^2[0-9][0-9]$ ]]; then
```

This is a low-priority suggestion since the spec is explicit about 200.

---

## Documentation Updates Needed

None required. The script header comment accurately describes behavior and references the retry
AP (ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E).
