# Exploration Summary — AgentFacade Gate 1

## Task
Define AgentFacade interface + SpawnedAgentHandle + AgentSignal + ContextWindowState + SpawnAgentConfig + AgentPayload.
Interfaces and data classes only — no implementations.

## Existing Types to Reuse (DO NOT DUPLICATE)
- `HandshakeGuid` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt` (value class)
- `ResumableAgentSessionId` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt` (data class with handshakeGuid, agentType, sessionId, model)
- `AgentType` — `app/src/main/kotlin/com/glassthought/shepherd/core/data/AgentType.kt` (enum: CLAUDE_CODE, PI)
- `TmuxAgentSession` — `app/src/main/kotlin/com/glassthought/shepherd/core/agent/TmuxAgentSession.kt`

## ContextWindowStateReader — NO existing Kotlin file
- Referenced in specs as ref.ap.ufavF1Ztk6vm74dLAgANY.E but no `.kt` file exists yet
- `ContextWindowState` data class must be created fresh — no duplication risk
- Per ticket note: verify before creating. VERIFIED — does not exist.

## Target Package
`com.glassthought.shepherd.core.agent.facade`

## Key Spec References
- AgentFacade spec: `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E)
- PartExecutor spec: `doc/core/PartExecutor.md` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E)
- AgentSignal: ref.ap.UsyJHSAzLm5ChDLd0H6PK.E (in PartExecutor.md)

## Interface Shape (from spec)
4 methods:
1. `spawnAgent(config: SpawnAgentConfig): SpawnedAgentHandle`
2. `sendPayloadAndAwaitSignal(handle: SpawnedAgentHandle, payload: AgentPayload): AgentSignal`
3. `readContextWindowState(handle: SpawnedAgentHandle): ContextWindowState`
4. `killSession(handle: SpawnedAgentHandle)`

## AgentSignal (from PartExecutor.md)
```kotlin
sealed class AgentSignal {
    data class Done(val result: DoneResult) : AgentSignal()
    data class FailWorkflow(val reason: String) : AgentSignal()
    data class Crashed(val details: String) : AgentSignal()
    object SelfCompacted : AgentSignal()
}
enum class DoneResult { COMPLETED, PASS, NEEDS_ITERATION }
```

## SpawnedAgentHandle (from AgentFacade.md)
- `guid: HandshakeGuid`
- `sessionId: ResumableAgentSessionId`
- Observable `lastActivityTimestamp: Instant`

## SpawnAgentConfig Fields (from ticket)
partName, subPartName, subPartIndex, agentType, model, role, systemPromptPath, bootstrapMessage

## AgentPayload (from ticket)
Carries the instruction file path.

## R2 Note (UserQuestionHandler)
Q&A handling is internal to `sendPayloadAndAwaitSignal`. KDoc should document this — no signature change needed.

## Acceptance Criteria
- All types compile
- KDoc on each interface method
- Interface methods match orchestration layer needs per PartExecutor.md
- No implementations — interfaces and data classes only
- Package: `com.glassthought.shepherd.core.agent.facade`
