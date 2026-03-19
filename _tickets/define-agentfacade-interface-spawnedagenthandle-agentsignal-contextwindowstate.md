---
id: nid_m7oounvwb31ra53ivu7btoj5v_E
title: "Define AgentFacade interface + SpawnedAgentHandle + AgentSignal + ContextWindowState"
status: open
deps: []
links: []
created_iso: 2026-03-19T00:28:58Z
status_updated_iso: 2026-03-19T00:28:58Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [core, agent-facade, interface]
---

## Context

Spec: `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E)

This is Gate 1 from the spec — define the contract before writing implementations.

## What to Implement

### 1. AgentFacade interface

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt`

```kotlin
interface AgentFacade {
    suspend fun spawnAgent(config: SpawnAgentConfig): SpawnedAgentHandle
    suspend fun sendPayloadAndAwaitSignal(handle: SpawnedAgentHandle, payload: AgentPayload): AgentSignal
    suspend fun readContextWindowState(handle: SpawnedAgentHandle): ContextWindowState
    suspend fun killSession(handle: SpawnedAgentHandle)
}
```

### 2. SpawnedAgentHandle data class

Contains:
- `guid: HandshakeGuid` (already exists at `core/agent/sessionresolver/HandshakeGuid.kt`)
- `sessionId: ResumableAgentSessionId` (already exists at `core/agent/sessionresolver/ResumableAgentSessionId.kt`)
- Observable `lastActivityTimestamp: Instant`

### 3. AgentSignal sealed class (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E)

```kotlin
sealed class AgentSignal {
    data class Done(val result: DoneResult) : AgentSignal()
    data class FailWorkflow(val reason: String) : AgentSignal()
    data class Crashed(val details: String) : AgentSignal()
    object SelfCompacted : AgentSignal()
}

enum class DoneResult {
    COMPLETED,
    PASS,
    NEEDS_ITERATION,
}
```

### 4. ContextWindowState data class

```kotlin
data class ContextWindowState(
    val remainingPercentage: Int?  // null = stale/unknown
)
```

### 5. SpawnAgentConfig + AgentPayload

Define input data classes for spawn and payload operations. SpawnAgentConfig should carry: partName, subPartName, subPartIndex, agentType, model, role, systemPromptPath, bootstrapMessage. AgentPayload should carry the instruction file path.

## Acceptance Criteria

- All types compile
- KDoc on each interface method describing contract
- Interface methods match the orchestration layer needs per `doc/core/PartExecutor.md`
- No implementations in this ticket — interfaces and data classes only
- Place in package `com.glassthought.shepherd.core.agent.facade`


## Notes

**2026-03-19T00:33:53Z**

## Review feedback — ContextWindowState duplication risk

Before defining ContextWindowState, verify whether it already exists in the codebase (ContextWindowStateReader ref.ap.ufavF1Ztk6vm74dLAgANY.E may already define it). Reuse existing type if present. Do NOT create a duplicate.

Also: R2 from spec (UserQuestionHandler interaction design) says "Must resolve at Gate 1." Ensure the interface shape accounts for how Q&A suppression interacts with sendPayloadAndAwaitSignal — the method signature does not need to change, but KDoc should document that Q&A handling is internal to the method.
