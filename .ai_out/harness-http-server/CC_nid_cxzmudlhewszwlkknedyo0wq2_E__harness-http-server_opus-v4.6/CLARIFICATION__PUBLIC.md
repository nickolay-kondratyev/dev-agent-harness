# Clarification: Harness HTTP Server

## Status: No Ambiguities Found

The ticket is well-specified. Requirements are clear:

1. **Endpoints** — 4 POST endpoints with JSON payloads confirmed by CLI script analysis:
   - `/agent/done` — `{branch: string}`
   - `/agent/question` — `{branch: string, question: string}`
   - `/agent/failed` — `{branch: string, reason: string}`
   - `/agent/status` — `{branch: string}`

2. **Port management** — Port 0 binding, write to `$HOME/.chainsaw_agent_harness/server/port.txt`, delete on shutdown

3. **Package** — `com.glassthought.chainsaw.core.server`

4. **Dependencies** — Ktor CIO + content negotiation + Jackson (already in project)

5. **Resource lifecycle** — Implements `AsgardCloseable` for proper shutdown

6. **Testing** — Clear acceptance criteria in ticket

7. **Stubs only** — Endpoints accept requests, log receipt, return 200. No real handler logic.

Proceeding to DETAILED_PLANNING.
