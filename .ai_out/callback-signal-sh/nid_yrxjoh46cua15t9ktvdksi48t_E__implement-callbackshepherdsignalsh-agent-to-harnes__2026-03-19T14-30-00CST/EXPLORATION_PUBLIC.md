# Exploration: callback_shepherd.signal.sh

## Key Findings

### Script Location
- Target: `app/src/main/resources/scripts/callback_shepherd.signal.sh`
- `app/src/main/resources/` does not exist yet — needs to be created
- No existing scripts in the project (greenfield)

### Spec Reference
- Full spec: `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E)
- Retry section: ref.ap.yzc3Q5TEh2EYCN03J7ZuL.E

### Six Signal Actions
| Action | Extra Args | JSON Payload |
|--------|-----------|-------------|
| `started` | None | `{ "handshakeGuid": "$GUID" }` |
| `done` | `<result>` (completed/pass/needs_iteration) | `{ "handshakeGuid": "$GUID", "result": "$RESULT" }` |
| `user-question` | `"<text>"` | `{ "handshakeGuid": "$GUID", "question": "$TEXT" }` |
| `fail-workflow` | `"<reason>"` | `{ "handshakeGuid": "$GUID", "reason": "$REASON" }` |
| `ack-payload` | `<payload-id>` | `{ "handshakeGuid": "$GUID", "payloadId": "$PAYLOAD_ID" }` |
| `self-compacted` | None | `{ "handshakeGuid": "$GUID" }` |

### Retry Policy
- 3 total attempts (2 retries), backoff: 1s then 5s
- Transient: connection refused, reset, timeout, HTTP 5xx → retry
- Non-transient: HTTP 400, 404, missing env vars → fail immediately

### Validation Rules
- `done`: only `completed`, `pass`, `needs_iteration` accepted
- `ack-payload`: exactly one arg required
- Unknown action → error + non-zero exit
- Missing env vars → fail-fast before HTTP

### Existing Code
- `AgentSignal.kt` has `DoneResult` enum: COMPLETED, PASS, NEEDS_ITERATION
- No existing bash tests in project

### Dependencies
- ShepherdServer (nid_gpfjqkfrmfbvhm1gan31rlvs9_E) not yet implemented — integration tests deferred
- Script is standalone bash, uses `curl`
