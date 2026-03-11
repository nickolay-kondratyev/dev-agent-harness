---
closed_iso: 2026-03-11T22:57:12Z
id: nid_e17r2p173udtcuipttb4w4gsu_E
title: "Add HandshakeGuid to ResumableAgentSessionId"
status: closed
deps: [nid_lzjoy65o0vxu4lt1utk9e6iy6_E]
links: []
created_iso: 2026-03-11T20:43:08Z
status_updated_iso: 2026-03-11T22:57:12Z
type: task
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [spawn]
---

Per spec (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E), SessionsState tracks TmuxAgentSession instances keyed by HandshakeGuid. The HandshakeGuid must travel with the session identity so the server can route incoming callbacks.

## File to modify
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt`

## Change
Add `handshakeGuid: HandshakeGuid` field to `ResumableAgentSessionId`:
```kotlin
data class ResumableAgentSessionId(
    val handshakeGuid: HandshakeGuid,
    val agentType: AgentType,
    val sessionId: String,
)
```

This aligns with the session schema in ref.ap.hZdTRho3gQwgIXxoUtTqy.E (SpawnTmuxAgentSessionUseCase.md) where each session record carries `handshake_guid` alongside `agent_session_id` and `agentType`.

## Why ResumableAgentSessionId and not TmuxAgentSession
The HandshakeGuid is part of session identity — it is generated during spawn and persisted to `current_state.json` for resume. It belongs with the other identity fields (`agentType`, `sessionId`), not as a separate field on the TMUX session wrapper.


## Notes

**2026-03-11T22:57:08Z**

## Resolution

Added `handshakeGuid: HandshakeGuid` as the first field in `ResumableAgentSessionId`.

**Files modified:**
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt` — added `handshakeGuid` field, updated KDoc
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt` — pass `guid` through when constructing `ResumableAgentSessionId`
- `app/src/test/kotlin/com/glassthought/ticketShepherd/core/sessionresolver/impl/ClaudeCodeAgentSessionIdResolverTest.kt` — updated 3 assertions to include `handshakeGuid`

All tests pass.
