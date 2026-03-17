---
closed_iso: 2026-03-17T22:17:03Z
id: nid_rchs79r92tshvjb3m2jpudb1f_E
title: "SIMPLIFY_CANDIDATE: Have agent self-report session ID in callback — eliminate post-spawn file scanning"
status: closed
deps: []
links: []
created_iso: 2026-03-17T22:02:14Z
status_updated_iso: 2026-03-17T22:17:03Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, session-id, agent-startup]
---


FEEDBACK:
--------------------------------------------------------------------------------
Currently session ID discovery is decoupled from agent spawn:
1. Harness spawns TMUX session (gets TMUX session name)
2. Harness calls AgentSessionIdResolver.resolve() to scan external artifacts (e.g., JSONL files written by Claude Code)
3. Discovery may fail if agent hasnt written the artifact yet
4. Harness retries with polling until the file appears — race condition

The session ID is essential state but discovered indirectly, asynchronously, and out-of-band from the spawn operation. This creates complex retry/polling logic and potential timing failures.

Proposed simplification:
- Agent reports its own session ID in the /callback-shepherd/signal/started payload (or first done signal if /started is eliminated per separate ticket)
- Agent derives its own session ID at startup (Claude Code can read ~/.claude_session or similar mechanism)
- Harness registers session ID at callback time — no file scanning needed
- AgentSessionIdResolver complexity removed entirely (or retained as a fallback interface)

Robustness gains:
- No polling/retry loop for session ID — immediate registration at callback time
- Agent-reported ID is authoritative (agent knows best what its own session ID is)
- Eliminates race conditions from file-system artifact scanning
- Simpler harness code: no resolver interface needed for V1
- Session ID always available when first done signal arrives

Note: If ticket "Merge /started into first done" is implemented, the session ID should be reported in the done signal payload instead.

Relevant specs:
- doc/core/agent-to-server-communication-protocol.md (signal payloads — add sessionId field)
- doc/use-case/SpawnTmuxAgentSessionUseCase.md (session ID resolution, post-spawn polling)
- doc/high-level.md (session ID tracking section)

Relevant code:
- app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/ (AgentSessionIdResolver)
- Server callback handler (session registration)
- SessionEntry (state where sessionId is stored)


--------------------------------------------------------------------------------
DECISION: NO. lets document that agents do NOT have their session id in their context. Hence, we need scanning with GUID handshake to find out their session id. This needs to be documented to avoid running into such advice for "simplification"
## Notes

**2026-03-17T22:17:00Z**

RESOLUTION: Decision documented in specs. Added explicit 'Why Not: Agent Self-Reporting (Rejected — Do Not Revisit)' subsection to doc/use-case/SpawnTmuxAgentSessionUseCase.md and updated doc/high-level.md (Session ID Tracking section + Key Technology Decisions table) to cross-reference the rejected approach. No code changes — spec-only update per task instructions.
