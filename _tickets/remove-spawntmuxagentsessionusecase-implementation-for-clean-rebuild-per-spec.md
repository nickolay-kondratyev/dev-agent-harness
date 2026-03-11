---
closed_iso: 2026-03-11T21:30:54Z
id: nid_lzjoy65o0vxu4lt1utk9e6iy6_E
title: "Remove SpawnTmuxAgentSessionUseCase implementation for clean rebuild per spec"
status: closed
deps: []
links: []
created_iso: 2026-03-11T20:42:18Z
status_updated_iso: 2026-03-11T21:30:54Z
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


## Notes

**2026-03-11T21:36:13Z**

## Resolution

Completed successfully. Removed all misaligned files and their orphaned dependencies:

**Removed (10 files):**
- `SpawnTmuxAgentSessionUseCase.kt` (the explicitly misaligned use case)
- `StartAgentRequest.kt` + `PhaseType.kt` (data classes tied to the use case)
- `AgentStarterBundleFactory.kt` + `AgentTypeChooser.kt` (interfaces exclusively supporting it)
- `ClaudeCodeAgentStarterBundleFactory.kt` + `AgentStarterBundle.kt` (implementations)
- `SpawnTmuxAgentSessionUseCaseIntegTest.kt` + 2 unit tests for removed factories

**Modified (6 files):**
- `Initializer.kt` — removed `UseCases` data class, simplified `initialize()` signature (dropped 3 params used only by removed bundle factory)
- `SharedContextIntegFactory.kt` — updated to match new signature
- `AppDependenciesCloseTest.kt` — updated to match new signature
- KDoc cleanups in `TmuxAgentSession.kt`, `ClaudeCodeAgentStarter.kt`, `HandshakeGuid.kt`

**Result:** Build SUCCESS, 115 tests pass, zero stale references remaining.
