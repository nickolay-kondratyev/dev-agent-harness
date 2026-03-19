---
id: nid_74c83cczud9jnwk1l1ywc8w2y_E
title: "Implement performCompaction() + done-boundary trigger detection in PartExecutorImpl"
status: in_progress
deps: [nid_5z93biuqub3mhcejfpofjmj39_E, nid_kwebixws6qqn808x4904f2gtr_E, nid_6kqfuee0ryuf45se8c06t6v3a_E, nid_i28ll9uk8k9uvdv3gus2izj4o_E, nid_mebn70o7xjiabzx5uxngjx8uf_E]
links: []
created_iso: 2026-03-19T00:42:28Z
status_updated_iso: 2026-03-19T19:52:30Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, compaction]
---

## Context

Spec: `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E), R2 + R3 + R7.
PartExecutor spec: ref.ap.fFr7GUmCYQEV5SJi8p6AS.E
PartExecutorImpl: ref.ap.mxIc5IOj6qYI7vgLcpQn5.E

This is Gate 3 from the compaction spec. The compaction flow, trigger detection, and session rotation logic all live in PartExecutorImpl (or a helper class extracted from it). Uses FakeAgentFacade for testing.

This extends PartExecutorImpl (nid_5z93biuqub3mhcejfpofjmj39_E) with self-compaction. It may be implemented as part of that ticket or as a follow-up — the key point is the compaction logic is well-defined and testable.

## What to Implement

### 1. performCompaction(handle, trigger: CompactionTrigger.DONE_BOUNDARY)

Location: Within PartExecutorImpl or as a helper class used by it.

Flow:
```
performCompaction(handle, DONE_BOUNDARY):
  1. Pre-compaction: no-op (agent already idle at done boundary)
  2. Core compaction:
     a. Build compaction instruction via SelfCompactionInstructionBuilder
     b. Send via agentFacade.sendPayloadAndAwaitSignal(handle, compactionPayload)
        - Timeout: HarnessTimeoutConfig.selfCompactionTimeout
        - Expected signal: AgentSignal.SelfCompacted
        - If AgentSignal.Done received → immediate AgentCrashed("agent cannot follow compaction protocol")
     c. Validate PRIVATE.md exists and is non-empty
     d. Git commit via GitCommitStrategy
     e. Kill session via agentFacade.killSession(handle)
  3. Post-compaction: handle = null (lazy respawn)
```

### 2. Done-boundary trigger detection (R3)

After AgentSignal.Done + PUBLIC.md validation:
```kotlin
val contextState = agentFacade.readContextWindowState(handle)
if (contextState.remainingPercentage == null) {
    // Log warning: stale context_window_slim.json — skip compaction
    // Continue normal flow
} else if (contextState.remainingPercentage <= harnessTimeoutConfig.contextWindowSoftThresholdPct) {
    performCompaction(handle, CompactionTrigger.DONE_BOUNDARY)
    handle = null  // lazy respawn
}
```

### 3. Session rotation (R7)

After performCompaction sets handle = null:
- Next iteration detects handle == null
- Calls agentFacade.spawnAgent(config) → fresh session
- Sends instructions via agentFacade.sendPayloadAndAwaitSignal
- PRIVATE.md is included via ContextForAgentProvider (privateMdPath field)
- New HandshakeGuid, new session record

This is the SAME first-iteration code path already used by PartExecutorImpl — no special-case logic needed.

### 4. CompactionTrigger enum

```kotlin
enum class CompactionTrigger {
    /** Agent at done boundary with <=35% context remaining. */
    DONE_BOUNDARY,
}
```

Note: V1 has only one trigger. The enum exists for extensibility (V2 adds EMERGENCY_INTERRUPT).

## Tests (all using FakeAgentFacade)

1. DONE_BOUNDARY: done → low context (remaining=20) → compaction instruction sent → SelfCompacted received → PRIVATE.md validated → session killed → handle = null
2. DONE_BOUNDARY: done → healthy context (remaining=80) → NO compaction → normal flow continues
3. DONE_BOUNDARY: done → stale context (remaining=null) → NO compaction → warning logged → normal flow continues
4. Strict enforcement: done signal received during compaction → immediate AgentCrashed
5. Session rotation: after compaction → next use spawns new session → PRIVATE.md in instructions
6. PRIVATE.md missing after SelfCompacted signal → AgentCrashed
7. PRIVATE.md empty after SelfCompacted signal → AgentCrashed
8. Compaction timeout → AgentCrashed

## Acceptance Criteria

- performCompaction() implements full done-boundary flow
- Trigger detection works at done boundaries
- Session rotation produces working new session with PRIVATE.md
- All unit tests pass using FakeAgentFacade
- `./test.sh` passes

