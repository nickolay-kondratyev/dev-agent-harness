---
id: nid_gpfjqkfrmfbvhm1gan31rlvs9_E
title: "Implement ShepherdServer — Ktor CIO HTTP server with signal routing"
status: open
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_v14amda2uv5nedrp9hvb8xlfq_E]
links: [nid_yrxjoh46cua15t9ktvdksi48t_E]
created_iso: 2026-03-19T00:40:50Z
status_updated_iso: 2026-03-19T00:40:50Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, protocol, server]
---

## Context

Spec: `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E)

The ShepherdServer is the Ktor CIO HTTP server that receives all agent-to-harness signals. It binds to the port specified by `TICKET_SHEPHERD_SERVER_PORT` env var and routes callbacks to SessionsState.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/server/ShepherdServer.kt`

### Server Setup
- Ktor CIO embedded server
- Port from env var `TICKET_SHEPHERD_SERVER_PORT`
- Fail hard if port already in use (clear error message)
- Jackson content negotiation (Ktor deps already in build.gradle.kts)
- Server starts once at harness startup, stays alive across all sub-parts

### Signal Endpoints (fire-and-forget — bare 200 OK)

All endpoints: `POST /callback-shepherd/signal/{action}`

All requests include `handshakeGuid` field. Server looks up in SessionsState.

| Endpoint | Behavior |
|---|---|
| `/signal/started` | Side-channel. Update `lastActivityTimestamp`. Complete startup deferred (not `signalDeferred`). Return 200. |
| `/signal/done` | Lifecycle. Validate `result` field per sub-part role (see Result Validation). Complete `signalDeferred` with `AgentSignal.Done(result)`. Return 200. |
| `/signal/user-question` | Side-channel. Append question to `SessionEntry.questionQueue`. Update `lastActivityTimestamp`. Return 200. |
| `/signal/fail-workflow` | Lifecycle. Complete `signalDeferred` with `AgentSignal.FailWorkflow(reason)`. Return 200. |
| `/signal/self-compacted` | Lifecycle. Complete `signalDeferred` with `AgentSignal.SelfCompacted`. Return 200. |
| `/signal/ack-payload` | Side-channel. Clear `pendingPayloadAck` when `payloadId` matches. Update `lastActivityTimestamp`. Return 200. |

### Result Validation on /signal/done

The `result` field is required. Server looks up sub-part role via HandshakeGuid -> SessionEntry -> SubPartRole.fromIndex(subPartIndex):

| Role | Valid results | Invalid -> 400 |
|---|---|---|
| DOER (index 0) | `completed` | `pass`, `needs_iteration`, any other |
| REVIEWER (index 1) | `pass`, `needs_iteration` | `completed`, any other |

- Missing `result` field -> 400
- Role-mismatched value -> 400

### Unknown HandshakeGuid
- `SessionsState.lookup(guid)` returns null -> 404 + WARN log
- Distinct from "already completed" idempotent case (which returns 200)

### Idempotent Signal Callbacks

When server receives lifecycle signal for already-completed deferred:

**True duplicates** (done after done, fail after fail, done after fail):
- Return 200
- Log WARN

**Late fail-workflow after done:**
- Return 200
- Log ERROR (not WARN) — the done result stands
- No halt propagation

### ack-payload Edge Cases

**Mismatched PayloadId:** payloadId does not match `pendingPayloadAck` -> return 200, log WARN, do NOT clear pendingPayloadAck. Timestamp still updated.

**Duplicate ACK:** `pendingPayloadAck` already null -> return 200, log WARN.

### Request Payload DTOs

```kotlin
data class SignalStartedRequest(val handshakeGuid: String)
data class SignalDoneRequest(val handshakeGuid: String, val result: String)
data class SignalUserQuestionRequest(val handshakeGuid: String, val question: String)
data class SignalFailWorkflowRequest(val handshakeGuid: String, val reason: String)
data class SignalAckPayloadRequest(val handshakeGuid: String, val payloadId: String)
data class SignalSelfCompactedRequest(val handshakeGuid: String)
```

### Server Port Env Var
Add `TICKET_SHEPHERD_SERVER_PORT` to Constants.AGENT_COMM (already has HANDSHAKE_GUID_ENV_VAR there).

## Dependencies
- SessionsState + SessionEntry (nid_erd0khe8sg0vqbnwtg23aqzw9_E)
- AgentSignal sealed class (nid_m7oounvwb31ra53ivu7btoj5v_E)
- Ktor dependencies already in `app/build.gradle.kts`
- `OutFactory` for structured logging

## Testing
- Unit tests using Ktor testApplication:
  - POST /signal/done with valid handshakeGuid and valid result -> 200 + deferred completed
  - POST /signal/done with missing result -> 400
  - POST /signal/done with role-mismatched result -> 400
  - POST /signal/done with unknown GUID -> 404
  - POST /signal/done duplicate (deferred already completed) -> 200 + WARN logged
  - POST /signal/fail-workflow after done -> 200 + ERROR logged
  - POST /signal/started -> 200 + timestamp updated
  - POST /signal/user-question -> 200 + question appended to queue
  - POST /signal/ack-payload with matching payloadId -> 200 + pendingPayloadAck cleared
  - POST /signal/ack-payload with mismatched payloadId -> 200 + WARN + pendingPayloadAck NOT cleared
  - POST /signal/ack-payload when already null -> 200 + WARN

