---
closed_iso: 2026-03-11T20:42:48Z
id: nid_e0525c8z4eiu1ktey7yu868yv_E
title: "Remove pre-spec server implementation (HarnessServer, AgentRequests, AgentRequestHandler) for clean rebuild"
status: closed
deps: []
links: []
created_iso: 2026-03-11T20:37:21Z
status_updated_iso: 2026-03-11T20:42:48Z
type: chore
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [server, cleanup]
---

The current server implementation predates the spec and is fundamentally misaligned.

## What to remove
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/HarnessServer.kt` (KtorHarnessServer)
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/AgentRequests.kt` (AgentDoneRequest, AgentQuestionRequest, AgentFailedRequest, AgentStatusRequest)
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/AgentRequestHandler.kt` (interface + NoOpAgentRequestHandler)
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/server/PortFileManager.kt` (if tightly coupled)
- All corresponding tests under `app/src/test/kotlin/.../server/`

## Why
Misalignments vs spec (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E):
1. **Endpoint paths**: Code uses `/agent/done`, `/agent/question`, `/agent/failed`, `/agent/status`. Spec requires `/callback-shepherd/done`, `/callback-shepherd/user-question`, `/callback-shepherd/fail-workflow`, `/callback-shepherd/ping-ack`.
2. **Identity model**: Code uses `branch` field. Spec requires `handshakeGuid` field with SessionsState-based routing.
3. **`/user-question` is blocking**: Code holds HTTP connection open returning answer. Spec requires non-blocking 200 + TMUX delivery.
4. **`/done` missing `result` field**: Code has no `result` (completed/pass/needs_iteration). Spec requires it with role-based validation.

## What to preserve
The technology choices ARE already captured in `doc/high-level.md` (line 277: Ktor CIO, line 278: port 0 OS-assigned) and `doc/core/agent-to-server-communication-protocol.md`. No information is lost by removing the code.

## Rebuild will follow spec
New implementation will be built per ref.ap.wLpW8YbvqpRdxDplnN7Vh.E with HandshakeGuid-based routing, non-blocking callbacks, and result validation.


## Notes

**2026-03-11T20:42:56Z**

Completed: Deleted 6 files (4 source + 2 test) under app/src/*/server/. Zero external references confirmed by exploration. No new test failures introduced — TicketParserTest and RoleCatalogLoaderTest failures were pre-existing. Follow-up ticket nid_611rashdkhgxp74gwwkvpeo35_E created for those.
