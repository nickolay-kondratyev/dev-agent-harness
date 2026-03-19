---
id: nid_t7dnd545hf343i2v7jqcke3x7_E
title: "Implement SpawnTmuxAgentSessionUseCase — Phase 1 bootstrap spawn flow"
status: open
deps: [nid_3rw8eoib2wseydcyhnj648d2a_E, nid_m3cm8xizw5qhu1cu3454rca79_E, nid_0of6zl2493ctvmy9m23kxnhnl_E]
links: []
created_iso: 2026-03-19T00:17:35Z
status_updated_iso: 2026-03-19T00:17:35Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [spawn, use-case, core]
---

## Context

Spec: `doc/use-case/SpawnTmuxAgentSessionUseCase.md` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E)
Implementation ref: ref.ap.M1jzg6RlJkYL4hi8aXr7LnQA.E

This use case orchestrates spawning an agent in a TMUX session. It is called internally by `AgentFacadeImpl.spawnAgent()` (ref.ap.9h0KS4EOK5yumssRCJdbq.E). `PartExecutor` does NOT call it directly.

## What To Do

Implement `SpawnTmuxAgentSessionUseCase` class at `app/src/main/kotlin/com/glassthought/shepherd/core/usecase/SpawnTmuxAgentSessionUseCase.kt` covering Phase 1 (bootstrap spawn). Phase 2 (instruction delivery + signal-await) is owned by `AgentFacadeImpl.sendPayloadAndAwaitSignal()` — not part of this ticket.

### Phase 1: Bootstrap — Identity + Liveness Handshake
1. Generate `HandshakeGuid` (already exists at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt`)
2. Read `agentType` and `model` from sub-part config in `CurrentState` (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E)
3. Build TMUX start command via `AgentTypeAdapter.buildStartCommand(bootstrapMessage)` — bootstrap message contains HandshakeGuid + instruction to call `callback_shepherd.signal.sh started`
4. Create TMUX session via `TmuxSessionManager.createSession()` — agent starts interactively
5. Await `/callback-shepherd/signal/started` within `healthTimeouts.startup` (default 3 min)
6. On `/started` received:
   a. Call `AgentTypeAdapter.resolveSessionId(handshakeGuid)` to find agent session ID in JSONL artifacts
   b. Store session record in `CurrentState` under sub-part `sessionIds` array (ref.ap.mwzGc1hYkVwu3IJQbTeW4.E), flush to `current_state.json`
   c. Return `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E) as the live session handle
7. On timeout (no `/started` within startup timeout) → return failure signal for `AgentUnresponsiveUseCase(STARTUP_TIMEOUT)`

### TMUX Session Creation Failure — Hard Fail
If `tmux new-session` returns non-zero:
1. Log tmux error (stderr) via structured logging
2. Print red error to console
3. Return `AgentSignal.Crashed` — flows through existing crash handling path
4. No health monitoring loop entered

### Scope Boundary — Phase 1 Only
This use case owns **Phase 1 only** (bootstrap spawn: GUID gen → TMUX session creation → await `/started` → resolve session ID → return `TmuxAgentSession`). Phase 2 (instruction file writing, `AckedPayloadSender` delivery, health-aware signal-await loop) is owned by `AgentFacadeImpl.sendPayloadAndAwaitSignal()` and has its own tickets.

### Design Decisions — Handoff with AgentFacadeImpl
- **Return type**: `SpawnTmuxAgentSessionUseCase.execute()` returns `TmuxAgentSession` (ref.ap.DAwDPidjM0HMClPDSldXt.E) on success. The caller (`AgentFacadeImpl`) is responsible for registering the `SessionEntry` in `SessionsState` and constructing the `SpawnedAgentHandle`.
- **Startup await mechanism**: The use case receives a `CompletableDeferred<Unit>` (or equivalent) from `AgentFacadeImpl` that gets completed when the server receives `/callback-shepherd/signal/started` for this HandshakeGuid. The use case calls `withTimeout(healthTimeouts.startup) { startedDeferred.await() }`. This is a simple single-deferred timeout, NOT the full health-aware await loop (which is more complex and lives in `sendPayloadAndAwaitSignal`).
- **On TMUX creation failure**: The use case throws an exception (e.g., `TmuxSessionCreationException`). The caller (`AgentFacadeImpl`) catches it and completes the `signalDeferred` with `AgentSignal.Crashed`.
- `TmuxSessionName` format: `shepherd_${partName}_${subPartName}`

### Dependencies (Constructor Injection)
- `AgentTypeAdapter` (dispatched by `agentType`)
- `TmuxSessionManager`
- `HealthTimeouts` (for startup timeout value)

## Testing
- Unit tests with faked `AgentTypeAdapter` and `TmuxSessionManager`
- Test bootstrap message construction
- Test TMUX creation failure → AgentSignal.Crashed path
- Test startup timeout → AgentUnresponsiveUseCase path
- Test happy path: spawn → started → resolveSessionId → session record stored

## Spec References
- `doc/use-case/SpawnTmuxAgentSessionUseCase.md` — full spawn flow spec
- `doc/core/AgentFacade.md` — encapsulating facade (ref.ap.9h0KS4EOK5yumssRCJdbq.E)
- `doc/core/SessionsState.md` — session registry (ref.ap.7V6upjt21tOoCFXA7nqNh.E)

## Acceptance Criteria
- `SpawnTmuxAgentSessionUseCase` implements the Phase 1 bootstrap flow
- TMUX creation failure handled as hard fail with AgentSignal.Crashed
- Startup timeout correctly triggers failure path
- Session record stored in CurrentState on successful spawn
- Unit tests cover happy path, TMUX failure, and startup timeout
- `:app:test` passes

