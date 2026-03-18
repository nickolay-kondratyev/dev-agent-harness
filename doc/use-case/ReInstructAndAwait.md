# ReInstructAndAwait / ap.QZYYZ2gTi1D2SQ5IYxOU6.E

A focused use-case class that encapsulates the **"send instruction to an existing agent
session, await the next signal"** pattern used in the **granular feedback loop** and
**rejection negotiation** inside `PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) and
`RejectionNegotiationUseCase` (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E).

---

## Problem It Solves

The pattern "send an instruction to an already-alive agent session, await its next signal,
and map the raw `AgentSignal` variants to caller-appropriate outcomes" appears in multiple
locations within the **granular feedback loop** and **rejection negotiation**:

| # | Location | Trigger |
|---|----------|---------|
| 1 | Granular feedback loop — per-item doer delivery (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) | Send single feedback item to doer; await `done completed` |
| 2 | Granular feedback loop — rejection judgment (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) | Send rejection + reasoning to reviewer; await `pass` or `needs_iteration` |
| 3 | `RejectionNegotiationUseCase` (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) — reviewer insistence | Deliver "must comply" instruction to doer when reviewer insists |

Each caller previously hand-rolled **~15–20 lines of identical plumbing** per site:
calling `agentFacade.sendPayloadAndAwaitSignal`, mapping the raw `AgentSignal` variants
to caller-appropriate outcomes, and crash propagation.

`ReInstructAndAwait` collapses that boilerplate into **one call** — leaving only the
message content at each call site.

**Guard points use immediate crash, not this class.** Guard checks (PUBLIC.md missing,
PRIVATE.md missing, feedback files missing, resolution marker missing, part completion guard)
that fail after a `done` signal result in an immediate `PartResult.AgentCrashed` — no retry.
The **Payload Delivery ACK protocol** (ref.ap.tbtBcVN2iCl1xfHJthllP.E) already guarantees
the agent received and acknowledged the instruction before signaling done. Non-compliance
after ACK-confirmed delivery = broken agent; retry would mask that.

---

## Interface

```kotlin
// ap.QZYYZ2gTi1D2SQ5IYxOU6.E

sealed class ReInstructOutcome {
    /**
     * Agent responded to the instruction.
     * Caller handles the signal normally.
     */
    data class Responded(val signal: AgentSignal.Done) : ReInstructOutcome()

    /**
     * Agent signaled fail-workflow during the await.
     * Caller propagates as PartResult.FailedWorkflow(reason).
     */
    data class FailedWorkflow(val reason: String) : ReInstructOutcome()

    /**
     * Agent crashed or timed out during the await.
     * Caller propagates as PartResult.AgentCrashed(details).
     */
    data class Crashed(val details: String) : ReInstructOutcome()
}

interface ReInstructAndAwait {
    /**
     * Delivers an instruction message to an existing agent session and awaits
     * the next signal.
     *
     * Internally delegates to `agentFacade.sendPayloadAndAwaitSignal(handle, message)` —
     * the facade owns fresh deferred creation, SessionEntry re-registration, ACK delivery,
     * and the health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).
     *
     * Maps the raw `AgentSignal` to `ReInstructOutcome` for cleaner call-site `when` branches.
     * Returns Responded, FailedWorkflow, or Crashed.
     */
    suspend fun execute(
        handle: SpawnedAgentHandle,
        message: String,
    ): ReInstructOutcome
}
```

---

## Usage Pattern at Each Call Site

**Before (hand-rolled at each site):**

```
val signal = agentFacade.sendPayloadAndAwaitSignal(handle, message)
when (signal) {
    is AgentSignal.Crashed      -> return PartResult.AgentCrashed(signal.details)
    is AgentSignal.FailWorkflow -> return PartResult.FailedWorkflow(signal.reason)
    is AgentSignal.Done         -> { /* continue */ }
    is AgentSignal.SelfCompacted -> { /* handle */ }
}
// continue with response ...
```

**After (with ReInstructAndAwait):**

```kotlin
when (val outcome = reInstructAndAwait.execute(handle, message)) {
    is ReInstructOutcome.Responded     -> { /* handle signal */ }
    is ReInstructOutcome.Crashed       -> return PartResult.AgentCrashed(outcome.details)
    is ReInstructOutcome.FailedWorkflow -> return PartResult.FailedWorkflow(outcome.reason)
}
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `AgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) | Single entry point via `sendPayloadAndAwaitSignal(handle, message)` — the facade owns deferred lifecycle, ACK, and health-aware await loop internally |

`HarnessTimeoutConfig` and `Clock` are constructor dependencies of `AgentFacadeImpl`, not
`ReInstructAndAwait` — the facade handles all timing internally.

---

## Testability

`ReInstructAndAwait` is **independently unit-testable** via `FakeAgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E)
+ virtual time (`TestClock` + `kotlinx-coroutines-test`). It has no dependency on
`PartExecutorImpl`.

Key test scenarios:

| Scenario | What to verify |
|----------|----------------|
| Agent responds with done | `ReInstructOutcome.Responded` with the expected `AgentSignal.Done` |
| Agent crashes during await | `ReInstructOutcome.Crashed` with crash details |
| Agent signals fail-workflow | `ReInstructOutcome.FailedWorkflow` with reason |
| Agent self-compacts during await | `ReInstructOutcome.Responded` with `SelfCompacted` wrapped as continued-await (handled inside facade) |

Tests for `PartExecutorImpl` use this abstraction as a seam — the feedback loop instruction
delivery is tested at the `PartExecutorImpl` level with a programmable `FakeReInstructAndAwait`.

---

## Relationship to Existing Spec

- **PartExecutorImpl** (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E): Uses `ReInstructAndAwait` for
  feedback item delivery and the rejection negotiation paths. Guard checks (PUBLIC.md,
  feedback files presence, resolution marker, part completion) result in immediate
  `PartResult.AgentCrashed` — `ReInstructAndAwait` is not involved.
- **RejectionNegotiationUseCase** (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E): Uses
  `ReInstructAndAwait` for the reviewer judgment step and the "doer must comply" step.
- **AgentFacade** (ref.ap.9h0KS4EOK5yumssRCJdbq.E): Provides `sendPayloadAndAwaitSignal`
  — the single entry point that owns deferred lifecycle, ACK, and the health-aware await
  loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) internally. `ReInstructAndAwait` sits between
  `PartExecutorImpl` and `AgentFacade` — orchestration layer calls the abstraction,
  abstraction calls the facade method, maps `AgentSignal` to `ReInstructOutcome`.
