---
id: nid_p1w49sk0s2isnvcjbmhgapho7_E
title: "Implement AgentFacadeImpl — spawn, kill, readContextWindowState wiring"
status: open
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_3rw8eoib2wseydcyhnj648d2a_E, nid_t7dnd545hf343i2v7jqcke3x7_E, nid_xeq8q9q7xmr56x5ttr98br4z9_E]
links: []
created_iso: 2026-03-19T00:29:55Z
status_updated_iso: 2026-03-19T00:29:55Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [core, agent-facade, implementation]
---

## Context

Spec: `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E), R2.

The real implementation of AgentFacade that delegates to existing infra components. This ticket covers the wiring of spawnAgent, killSession, and readContextWindowState. The health-aware await loop inside sendPayloadAndAwaitSignal is covered by a separate ticket (nid_qdd1w86a415xllfpvcsf8djab_E).

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`

### Constructor Dependencies

```kotlin
class AgentFacadeImpl(
    private val sessionsState: SessionsState,
    private val agentTypeAdapter: AgentTypeAdapter,  // ref.ap.A0L92SUzkG3gE0gX04ZnK.E
    private val tmuxSessionManager: TmuxSessionManager,
    private val tmuxCommunicator: TmuxCommunicator,
    private val contextWindowStateReader: ContextWindowStateReader,  // ref.ap.ufavF1Ztk6vm74dLAgANY.E
    private val userQuestionHandler: UserQuestionHandler,  // ref.ap.NE4puAzULta4xlOLh5kfD.E
    private val agentUnresponsiveUseCase: AgentUnresponsiveUseCase,
    private val clock: Clock,  // ref.ap.whDS8M5aD2iggmIjDIgV9.E
    private val harnessTimeoutConfig: HarnessTimeoutConfig,
    private val outFactory: OutFactory,
) : AgentFacade
```

### Methods to Implement

1. **`spawnAgent(config)`**: Delegates to SpawnTmuxAgentSessionUseCase flow internally:
   - Generate HandshakeGuid
   - Build start command via AgentTypeAdapter
   - Start TMUX session via TmuxSessionManager
   - Resolve session ID via AgentTypeAdapter
   - Register initial SessionEntry in SessionsState (with fresh CompletableDeferred)
   - Return SpawnedAgentHandle

2. **`killSession(handle)`**: Kill TMUX session via TmuxSessionManager. Remove from SessionsState if present.

3. **`readContextWindowState(handle)`**: Delegate to ContextWindowStateReader. Return ContextWindowState with nullable remainingPercentage.

4. **`sendPayloadAndAwaitSignal(handle, payload)`**: Stub initially — full implementation in health-aware await loop ticket (nid_qdd1w86a415xllfpvcsf8djab_E). Can start with minimal version that creates fresh CompletableDeferred, re-registers, sends payload via TmuxCommunicator, and awaits signal without health monitoring.

### Key Design Points

- SessionsState is INTERNAL to this class — PartExecutor never touches it
- signalDeferred lifecycle is fully owned by this class
- Thread safety: all operations are suspend functions, use mutex where needed

## Acceptance Criteria

- AgentFacadeImpl compiles with all 4 methods
- spawnAgent correctly generates HandshakeGuid, delegates to adapter, registers SessionEntry
- killSession delegates to TmuxSessionManager
- readContextWindowState delegates to ContextWindowStateReader
- sendPayloadAndAwaitSignal has at minimum a working stub (create deferred, register, send, await)
- Unit tests verify wiring (mock/fake infra dependencies)
- `./test.sh` passes

