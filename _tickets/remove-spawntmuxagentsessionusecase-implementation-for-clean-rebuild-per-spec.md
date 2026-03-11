---
id: nid_lzjoy65o0vxu4lt1utk9e6iy6_E
title: "Remove SpawnTmuxAgentSessionUseCase implementation for clean rebuild per spec"
status: in_progress
deps: []
links: []
created_iso: 2026-03-11T20:42:18Z
status_updated_iso: 2026-03-11T21:05:06Z
type: chore
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [spawn, cleanup]
---

The current implementation self-declares misalignment: `TODO: MISALIGNED WITH SPEC. ADJUST TO ALIGN WITH SPEC.`

## What to remove
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/useCase/SpawnTmuxAgentSessionUseCase.kt`
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/data/StartAgentRequest.kt`
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/data/PhaseType.kt`
- `app/src/test/kotlin/com/glassthought/bucket/SpawnTmuxAgentSessionUseCaseIntegTest.kt`
- Remove `spawnTmuxAgentSession` from `UseCases` data class in `app/src/main/kotlin/com/glassthought/ticketShepherd/core/initializer/Initializer.kt`

## Misalignments vs spec (ref.ap.hZdTRho3gQwgIXxoUtTqy.E)
1. Uses `StartAgentRequest` with `PhaseType` enum — spec uses role + sub-part from workflow JSON (dynamic, not a closed set)
2. Returned `TmuxAgentSession` lacks `HandshakeGuid` — spec says SessionsState is keyed by HandshakeGuid
3. No resume flow — spec defines resume in SpawnTmuxAgentSessionUseCase.md:103-115
4. Does not export `TICKET_SHEPHERD_HANDSHAKE_GUID` as env var in the TMUX command (required for callback scripts)

## What to preserve — testing approach
The integration test pattern using `SharedContextDescribeSpec` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) is the RIGHT approach for testing this use case:
- Extends `SharedContextDescribeSpec` for shared `ShepherdContext` (process-scoped singleton)
- Accesses use case via `shepherdContext.useCases.spawnTmuxAgentSession`
- Gated with `isIntegTestEnabled()` (requires tmux + claude CLI)
- Cleans up TMUX sessions in `afterEach`
- Single `it` block for expensive spawn operations (API cost + time)

The rebuilt test should follow this same pattern, adapted to the new interface (role-based request instead of PhaseType enum).

## Spec for rebuild
See ref.ap.hZdTRho3gQwgIXxoUtTqy.E for full spawn/resume flow, HandshakeGuid contract, and session schema.

