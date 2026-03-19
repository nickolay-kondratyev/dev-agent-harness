---
id: nid_yrxjoh46cua15t9ktvdksi48t_E
title: "Implement callback_shepherd.signal.sh — agent-to-harness callback script"
status: in_progress
deps: []
links: [nid_gpfjqkfrmfbvhm1gan31rlvs9_E]
created_iso: 2026-03-19T00:41:14Z
status_updated_iso: 2026-03-19T15:08:16Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, protocol, bash]
---

## Context

Spec: `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E), sections "Callback Scripts" and "Retry on Transient Failures" (ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E)

## What to Implement

Location: `app/src/main/resources/scripts/callback_shepherd.signal.sh` (must be on agent $PATH)

### Behavior
- Reads port from `$TICKET_SHEPHERD_SERVER_PORT` env var
- Reads `$TICKET_SHEPHERD_HANDSHAKE_GUID` from environment; includes it in every request
- **Fail-fast:** hard-fail when either env var is not set
- Posts to `/callback-shepherd/signal/<action>`
- Expects 200 from server
- HTTP response body is ignored (fire-and-forget)

### Usage
```bash
callback_shepherd.signal.sh started                       # no extra args — bootstrap handshake
callback_shepherd.signal.sh done <result>                 # required: completed | pass | needs_iteration
callback_shepherd.signal.sh user-question "<text>"        # required: question text
callback_shepherd.signal.sh fail-workflow "<reason>"      # required: failure reason
callback_shepherd.signal.sh ack-payload <payload-id>      # required: PayloadId
callback_shepherd.signal.sh self-compacted                # no extra args
```

### Argument Validation
- `done`: reject anything other than `completed`, `pass`, or `needs_iteration` with clear error and non-zero exit
- `ack-payload`: require exactly one argument (the PayloadId). Missing or empty -> error and non-zero exit
- Unknown action -> error and non-zero exit

### JSON Payload Construction
Each action constructs its own JSON payload:
- `started`: `{ "handshakeGuid": "$GUID" }`
- `done`: `{ "handshakeGuid": "$GUID", "result": "$RESULT" }`
- `user-question`: `{ "handshakeGuid": "$GUID", "question": "$TEXT" }`
- `fail-workflow`: `{ "handshakeGuid": "$GUID", "reason": "$REASON" }`
- `ack-payload`: `{ "handshakeGuid": "$GUID", "payloadId": "$PAYLOAD_ID" }`
- `self-compacted`: `{ "handshakeGuid": "$GUID" }`

### Retry on Transient Failures (ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E)

| Parameter | Value |
|-----------|-------|
| Max retries | 2 (3 total attempts) |
| Backoff after 1st failure | 1 second |
| Backoff after 2nd failure | 5 seconds |
| After all retries exhausted | Exit non-zero |

**Transient (retry):** connection refused, connection reset, timeout, HTTP 5xx
**Not transient (fail immediately):** HTTP 400, HTTP 404, missing env vars

### Why Retry Is Safe
Signal endpoints are idempotent. Server handles duplicate callbacks gracefully (returns 200, logs WARN).

### The Deadlock This Prevents
Without retry, a lost `/signal/done` creates an unrecoverable deadlock:
1. Agent calls `done` -> transient HTTP failure -> script exits non-zero
2. Harness never completes `signalDeferred` -> waits indefinitely
3. Health monitor pings -> agent ACKs -> loop repeats forever

## Dependencies
- ShepherdServer must be running (nid_gpfjqkfrmfbvhm1gan31rlvs9_E) for integration testing
- No Kotlin dependencies — this is a standalone bash script

## Testing
- Unit test (bash): missing env vars -> exit non-zero with clear error
- Unit test (bash): invalid `done` result -> exit non-zero
- Unit test (bash): missing `ack-payload` argument -> exit non-zero
- Unit test (bash): unknown action -> exit non-zero
- Integration test: script + running ShepherdServer -> 200 response
- Integration test: retry on simulated transient failure (e.g., curl to port with nothing listening, then start server)

