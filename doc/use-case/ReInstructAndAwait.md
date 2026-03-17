# ReInstructAndAwait / ap.QZYYZ2gTi1D2SQ5IYxOU6.E

A focused use-case class that encapsulates the repeated **"send re-instruction to existing
agent session, await the next signal"** pattern found in 7+ locations throughout
`PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) and related components.

---

## Problem It Solves

The pattern "re-instruct agent once with context, then treat as `AgentCrashed`" appears in
every guard check inside `PartExecutorImpl`:

| # | Location | Trigger |
|---|----------|---------|
| 1 | PUBLIC.md Validation (ref.ap.THDW9SHzs1x2JN9YP9OYU.E) | `PUBLIC.md` missing or empty after `done` |
| 2 | PRIVATE.md check | `PRIVATE.md` missing after `self-compacted` signal |
| 3 | Feedback files presence guard (R9 in ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) | `pending/` empty after `needs_iteration` |
| 4 | Resolution marker guard | `## Resolution:` marker missing after doer `done` |
| 5 | Part completion guard (R8 in ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) | `critical__*`/`important__*` in `pending/` after reviewer `pass` |
| 6 | Bootstrap ACK re-delivery wrapper | Inline re-instruction around `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E) |
| 7 | `RejectionNegotiationUseCase` (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) retry path | Inline re-instruction when doer must comply after reviewer insistence |

Each caller previously hand-rolled **~15–20 lines of identical plumbing** per site:
calling `agentFacade.sendPayloadAndAwaitSignal`, mapping the raw `AgentSignal` variants
to caller-appropriate outcomes, and crash propagation.

`ReInstructAndAwait` collapses that boilerplate into **one call** — leaving only the
message content and condition re-check at each call site.

---

## Interface

```kotlin
// ap.QZYYZ2gTi1D2SQ5IYxOU6.E

sealed class ReInstructOutcome {
    /**
     * Agent responded to the re-instruction.
     * Caller re-checks its condition and handles the signal normally.
     */
    data class Responded(val signal: AgentSignal.Done) : ReInstructOutcome()

    /**
     * Agent signaled fail-workflow during the re-instruction await.
     * Caller propagates as PartResult.FailedWorkflow(reason).
     */
    data class FailedWorkflow(val reason: String) : ReInstructOutcome()

    /**
     * Agent crashed or timed out during the re-instruction await.
     * Caller propagates as PartResult.AgentCrashed(details).
     */
    data class Crashed(val details: String) : ReInstructOutcome()
}

interface ReInstructAndAwait {
    /**
     * Delivers a re-instruction message to an existing agent session and awaits
     * the next signal.
     *
     * Internally delegates to `agentFacade.sendPayloadAndAwaitSignal(handle, message)` —
     * the facade owns fresh deferred creation, SessionEntry re-registration, ACK delivery,
     * and the health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).
     *
     * Maps the raw `AgentSignal` to `ReInstructOutcome` for cleaner call-site `when` branches.
     * Returns Responded, FailedWorkflow, or Crashed.
     * The caller is responsible for re-checking its condition on Responded.
     */
    suspend fun execute(
        handle: SpawnedAgentHandle,
        message: String,
    ): ReInstructOutcome
}
```

---

## Usage Pattern at Each Call Site

**Before (hand-rolled at each of the 7+ sites):**

```
val signal = agentFacade.sendPayloadAndAwaitSignal(handle, message)
when (signal) {
    is AgentSignal.Crashed      -> return PartResult.AgentCrashed(signal.details)
    is AgentSignal.FailWorkflow -> return PartResult.FailedWorkflow(signal.reason)
    is AgentSignal.Done         -> { /* continue */ }
    is AgentSignal.SelfCompacted -> { /* handle */ }
}
// re-check condition ...
```

**After (with ReInstructAndAwait):**

```kotlin
when (val outcome = reInstructAndAwait.execute(handle, message)) {
    is ReInstructOutcome.Responded     -> { /* re-check condition, handle signal */ }
    is ReInstructOutcome.Crashed       -> return PartResult.AgentCrashed(outcome.details)
    is ReInstructOutcome.FailedWorkflow -> return PartResult.FailedWorkflow(outcome.reason)
}
```

---

## One Retry, Not Infinite

`ReInstructAndAwait` sends exactly **one** re-instruction and awaits exactly **one** response.
It does not loop. The "one retry, then AgentCrashed" contract is enforced at the call site:

```kotlin
// Call site: PUBLIC.md guard
if (!publicMdIsPresent(handle)) {
    out.warn("public_md_missing_after_done", ...)
    when (val outcome = reInstructAndAwait.execute(handle, REINSTRUCTIONS.publicMdMissing(path))) {
        is Responded -> {
            // Re-check — if still missing after explicit re-instruction, agent is broken
            if (!publicMdIsPresent(handle)) {
                return PartResult.AgentCrashed("Agent failed to produce PUBLIC.md after explicit re-instruction")
            }
            outcome.signal
        }
        is Crashed       -> return PartResult.AgentCrashed(outcome.details)
        is FailedWorkflow -> return PartResult.FailedWorkflow(outcome.reason)
    }
}
```

The **condition re-check after `Responded`** is the caller's responsibility — it may differ
per site (file existence, non-empty content, marker presence, etc.).

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
| Agent responds normally | `ReInstructOutcome.Responded` with the expected `AgentSignal.Done` |
| Agent crashes during re-instruction await | `ReInstructOutcome.Crashed` with crash details |
| Agent signals fail-workflow | `ReInstructOutcome.FailedWorkflow` with reason |
| Agent self-compacts during re-instruction | `ReInstructOutcome.Responded` with `SelfCompacted` wrapped as continued-await (handled inside facade) |

Tests for `PartExecutorImpl` use this abstraction as a seam — the full guard logic
(condition check → re-instruct → re-check → AgentCrashed if still fails) is tested
at the `PartExecutorImpl` level with a programmable `FakeReInstructAndAwait`.

---

## Relationship to Existing Spec

- **PartExecutorImpl** (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E): Each guard location in
  PartExecutorImpl is updated to call `reInstructAndAwait.execute(...)` rather than
  hand-rolling the await. The condition-check and AgentCrashed return remain in
  PartExecutorImpl.
- **RejectionNegotiationUseCase** (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E): Uses
  `ReInstructAndAwait` for the "doer must comply" step (reviewer insistence path).
- **AgentFacade** (ref.ap.9h0KS4EOK5yumssRCJdbq.E): Provides `sendPayloadAndAwaitSignal`
  — the single entry point that owns deferred lifecycle, ACK, and the health-aware await
  loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) internally. `ReInstructAndAwait` sits between
  `PartExecutorImpl` and `AgentFacade` — orchestration layer calls the abstraction,
  abstraction calls the facade method, maps `AgentSignal` to `ReInstructOutcome`.
