---
id: nid_3rw8eoib2wseydcyhnj648d2a_E
title: "Refactor AgentTypeAdapter — unify AgentStarter + AgentSessionIdResolver into single interface + ClaudeCodeAdapter"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T00:17:04Z
status_updated_iso: 2026-03-19T14:06:28Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [refactor, spawn, agent-adapter]
---

## Context

The spec at `doc/use-case/SpawnTmuxAgentSessionUseCase.md` (ref.ap.A0L92SUzkG3gE0gX04ZnK.E) defines `AgentTypeAdapter` as a **single interface** per agent type that encapsulates both `buildStartCommand()` and `resolveSessionId()`. Currently the codebase has two separate interfaces:

- `AgentStarter` at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/AgentStarter.kt`
- `AgentSessionIdResolver` at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/AgentSessionIdResolver.kt`

And two separate implementations:
- `ClaudeCodeAgentStarter` at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/impl/ClaudeCodeAgentStarter.kt`
- `ClaudeCodeAgentSessionIdResolver` at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt` (ref.ap.gCgRdmWd9eTGXPbHJvyxI.E)

## What To Do

1. Create `AgentTypeAdapter` interface with:
   ```kotlin
   interface AgentTypeAdapter {
       fun buildStartCommand(bootstrapMessage: String): TmuxStartCommand
       suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String
   }
   ```
   Note the signature changes:
   - `buildStartCommand` now accepts `bootstrapMessage: String` parameter (currently takes no args)
   - `resolveSessionId` returns `String` not `ResumableAgentSessionId` — the caller constructs the full `ResumableAgentSessionId`

2. Create `ClaudeCodeAdapter` implementing `AgentTypeAdapter` by composing/merging the existing `ClaudeCodeAgentStarter` and `ClaudeCodeAgentSessionIdResolver` logic.

3. Update `buildStartCommand` to embed the bootstrap message as the initial prompt argument in the `claude` CLI command (agent starts interactively and receives it atomically on startup).

4. Remove old `AgentStarter` and `AgentSessionIdResolver` interfaces and their implementations.

5. Update all callers — specifically:
   - `ShepherdContext` / `ContextInitializer` (at `app/src/main/kotlin/com/glassthought/shepherd/context/`) — wiring of adapter instances
   - `TmuxSessionManagerIntegTest` — may reference old `AgentStarter`
   - Note: `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) wires `AgentFacadeImpl` with the adapter — it is tracked separately (`nid_itogi6ji82dbhb0k3zzt6v8qp_E`) and will pick up this interface change when it implements. No coordination risk — this ticket defines the interface, that ticket consumes it.
   - Existing `ClaudeCodeAgentStarter` already handles `unset CLAUDECODE` — preserve this in `ClaudeCodeAdapter`.

6. Migrate existing unit tests from `ClaudeCodeAgentSessionIdResolverTest` (at `app/src/test/kotlin/com/glassthought/shepherd/core/sessionresolver/impl/ClaudeCodeAgentSessionIdResolverTest.kt`) to test the new `ClaudeCodeAdapter`.

## Spec References
- `doc/use-case/SpawnTmuxAgentSessionUseCase.md` — AgentTypeAdapter section (ref.ap.A0L92SUzkG3gE0gX04ZnK.E)
- `doc/high-level.md` — Sub-Agent Invocation section

## Acceptance Criteria
- `AgentTypeAdapter` interface exists with the two-method contract
- `ClaudeCodeAdapter` implements the interface, passing all migrated tests
- Old separate interfaces (`AgentStarter`, `AgentSessionIdResolver`) removed
- `buildStartCommand(bootstrapMessage)` embeds bootstrap message as positional CLI argument
- `:app:test` passes

