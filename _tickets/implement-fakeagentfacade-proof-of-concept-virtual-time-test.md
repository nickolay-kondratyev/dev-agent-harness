---
id: nid_g3e2bkvqepf2wzipadayt9in8_E
title: "Implement FakeAgentFacade + proof-of-concept virtual time test"
status: in_progress
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_xeq8q9q7xmr56x5ttr98br4z9_E, nid_c9jhmwry79x87j1h27qh4tvle_E]
links: []
created_iso: 2026-03-19T00:29:30Z
status_updated_iso: 2026-03-19T15:38:29Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [core, agent-facade, testing, fake]
---

## Context

Spec: `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E), R3 + Gate 2.

The FakeAgentFacade is the programmable test double that enables comprehensive unit testing of PartExecutorImpl without real agent infrastructure.

## What to Implement

Location: `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt`

### FakeAgentFacade

Implements `AgentFacade` with full programmatic control:

- **Spawn behavior**: configurable success/failure/delay. Returns pre-built SpawnedAgentHandle.
- **sendPayloadAndAwaitSignal return value**: queue of pre-programmed `AgentSignal` variants. Each call dequeues the next signal. Optionally support suspend delay to simulate latency.
- **readContextWindowState return value**: programmable `ContextWindowState` (any remaining percentage, or null for stale).
- **killSession**: records that it was called, with which handle.
- **Interaction verification**: methods to assert what was sent (payloads), what was read, was session killed, and in what order.

The fake does NOT re-run the health-aware loop — it simply returns pre-programmed signals.

### Proof-of-Concept Test

One test that verifies all three virtual time components work together:

```kotlin
// Pseudocode
runTest {
    val fake = FakeAgentFacade()
    fake.onSpawn { handle }
    fake.onSendPayloadAndAwaitSignal { AgentSignal.Done(DoneResult.COMPLETED) }
    fake.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }
    // Use the fake with a minimal executor scenario
    // Verify: spawn called, signal received, interactions recorded
}
```

## Acceptance Criteria

- FakeAgentFacade compiles and implements AgentFacade interface
- Proof-of-concept test passes using `runTest {}` with FakeAgentFacade + TestClock
- Fake is ergonomic: test setup is concise, readable
- Interaction verification works (assert payloads sent, kill called, etc.)
- `./test.sh` passes


## Notes

**2026-03-19T00:34:02Z**

## Review feedback — PoC test scope + DispatcherProvider

1. The proof-of-concept test should be **standalone** — directly use FakeAgentFacade WITHOUT PartExecutorImpl (which does not exist yet). Test that: (a) fake returns pre-programmed signals, (b) interaction verification works, (c) runTest {} + TestClock + advanceTimeBy interoperate correctly in a standalone test.

2. Additional acceptance criterion: Verify that DispatcherProvider is injectable so tests can route all coroutines through the test dispatcher. This is spec Risk R5 — "Must verify at Gate 2."
