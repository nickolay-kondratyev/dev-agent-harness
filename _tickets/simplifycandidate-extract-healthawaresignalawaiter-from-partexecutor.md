---
closed_iso: 2026-03-17T17:31:36Z
id: nid_2fjt9y0umyucxnzsmmnu08vip_E
title: "SIMPLIFY_CANDIDATE: Extract HealthAwareSignalAwaiter from PartExecutor"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:03:53Z
status_updated_iso: 2026-03-17T17:31:36Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, partexecutor, health]
---

The PartExecutor (doc/core/PartExecutor.md) is the most complex component in the system. Its spec contains a ~100-line health-aware await loop pseudocode that interleaves multiple concerns:
- Signal waiting (CompletableDeferred)
- Context window state checking (1-second polling)
- Stale context guard
- Dual-signal liveness monitoring (lastActivityTimestamp + fileUpdatedTimestamp)
- Early ping for in-session death detection
- Ping suppression logic
- Health check scheduling
- Crash detection
- ACK-await phase

This await loop is embedded inline in the PartExecutor, making the executor responsible for BOTH workflow orchestration AND health monitoring mechanics.

## Proposal
Extract the health-aware await loop into its own component: `HealthAwareSignalAwaiter`.

Interface:
```kotlin
interface HealthAwareSignalAwaiter {
    suspend fun awaitSignal(session: SpawnedAgentHandle, config: HealthConfig): AgentSignal
}
```

The PartExecutor calls `awaiter.awaitSignal(handle, config)` and gets back an AgentSignal. All health monitoring mechanics (polling, pinging, crash detection, context window checking) are encapsulated.

## Benefits
- SIMPLER: PartExecutor focuses on workflow orchestration (its primary concern). Health monitoring is a separate, focused component.
- MORE ROBUST: The await loop becomes independently unit-testable with FakeAgentFacade + virtual time. Currently, testing the await loop requires testing through the full PartExecutor flow.
- Clearer boundaries: health monitoring changes don't require PartExecutor changes and vice versa
- Reduces PartExecutor spec/code from ~500 lines to ~300 lines

## Affected Specs
- doc/core/PartExecutor.md (extract health-aware await loop, replace with awaiter call)
- doc/use-case/HealthMonitoring.md (HealthAwareSignalAwaiter becomes the home for the await loop spec)
- doc/use-case/ContextWindowSelfCompactionUseCase.md (context window polling moves into awaiter)

## Risk
- Low: This is a pure extraction refactor. No behavior changes. The await loop interface is clean (session in, signal out). The PartExecutor already treats the await as a black box conceptually — this just makes it a literal black box.


## Notes

**2026-03-17T17:31:45Z**

Closed as no-longer-needed. Recent simplifications (drop dual-signal liveness model, eliminate late-fail-workflow checkpoint) already reduced PartExecutor complexity significantly. The remaining health-aware await loop is ~60 lines of clean linear pseudocode. Extraction would redistribute complexity rather than eliminate it — especially problematic given emergency compaction changes sessions mid-await, requiring the extracted component to take most of the executor's dependencies. Poor ROI.
