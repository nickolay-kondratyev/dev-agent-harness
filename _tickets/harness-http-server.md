---
id: nid_cxzmudlhewszwlkknedyo0wq2_E
title: "Harness HTTP Server"
status: in_progress
deps: [nid_w5b16tby0fjiovxfr3ft22ix2_E]
links: []
created_iso: 2026-03-09T23:07:41Z
status_updated_iso: 2026-03-10T15:31:06Z
type: feature
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [wave1, server, ktor]
---

Implement the Ktor CIO HTTP server for agent→harness communication.

## Scope
- Create `HarnessServer` interface + implementation using Ktor CIO
- Bind to port 0 (OS-assigned random port)
- On startup: write assigned port to `$HOME/.chainsaw_agent_harness/server/port.txt`
- On shutdown: delete port file
- Endpoint stubs (accept requests, return 200, log receipt):
  - `POST /agent/done` — agent completed its task
  - `POST /agent/question` — agent has a question (will block in real impl, stub returns immediately)
  - `POST /agent/failed` — agent hit unrecoverable error
  - `POST /agent/status` — agent responds to health ping
- All endpoints accept JSON body with at minimum a `branch` field
- Add **Ktor CIO** dependencies to `app/build.gradle.kts`
- Package: `com.glassthought.chainsaw.core.server`

## Dependencies
- Workflow JSON Parser (nid_w5b16tby0fjiovxfr3ft22ix2_E) must be merged first to avoid build.gradle.kts merge conflicts (both add dependencies)

## Key Decisions
- Port 0 for OS-assigned port — eliminates collision risk
- Port file is the ONLY discovery mechanism (no env vars)
- Server starts once at harness startup, stays alive across all workflow phases
- Implements AsgardCloseable for proper shutdown (delete port file + stop server)
- Endpoints are stubs in this ticket — real handler logic wired in during integration
- Use Ktor content negotiation with Jackson for JSON request parsing

## Testing
- Unit/integration tests:
  - Test: server starts and port file is created with valid port number
  - Test: server shutdown deletes port file
  - Test: each endpoint returns 200 on valid POST
  - Test: port file contains the actual bound port
  - Test: can make HTTP requests to the server using the port from the file

## Files touched
- `app/build.gradle.kts` (add Ktor CIO + content-negotiation + jackson dependencies)
- New files under `app/src/main/kotlin/com/glassthought/chainsaw/core/server/`
- New files under `app/src/test/kotlin/com/glassthought/chainsaw/core/server/`

## Reference
- See "Agent↔Harness Communication" and "V1 Server Endpoints" sections in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`

## Completion Criteria — Anchor Point
As part of closing this ticket:
1. Run `anchor_point.create` to generate a new AP for this component.
2. Add `ap.XXX.E` just below the `## Agent↔Harness Communication — Bidirectional` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`.
3. Add `ref.ap.XXX.E` in the KDoc of the `HarnessServer` interface pointing back to that design ticket section.

