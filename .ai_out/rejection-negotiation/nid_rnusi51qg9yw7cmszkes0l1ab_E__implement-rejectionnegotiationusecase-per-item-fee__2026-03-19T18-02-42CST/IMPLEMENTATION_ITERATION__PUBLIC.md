# Implementation Iteration: RejectionNegotiationUseCase

## Review Feedback Evaluation

### Issue 1: Doer compliance message missing reviewer's counter-reasoning — REJECTED

**Reviewer's concern:** The spec (granular-feedback-loop.md lines 346-348) calls for including the reviewer's counter-reasoning in the doer compliance message.

**Rejection rationale:** `ReInstructOutcome.Responded` only carries `AgentSignal.Done(result)` with a `DoneResult` enum. The protocol does not support passing textual reasoning back through signals. The spec references "reviewer's counter-reasoning from PUBLIC.md" but does not specify:
- Where the reviewer writes counter-reasoning during the negotiation signal flow.
- How the harness reads it from the signal.

This is a **spec gap**, not an implementation oversight. Including counter-reasoning would require a protocol extension to `ReInstructOutcome.Responded` (carrying message payload alongside the signal). This is a V2 consideration.

**Action taken:** Added a WHY-NOT comment on `buildDoerComplianceMessage` documenting the spec gap and the protocol limitation. The generic authority message is pragmatic for V1.

### Issue 2: Self-compaction check between negotiation steps — REJECTED

**Reviewer's concern:** Gate 4 requires a unit test for self-compaction check between negotiation steps.

**Rejection rationale:** Self-compaction (`SelfCompacted` signal) is handled transparently at the `ReInstructAndAwait` level. When an agent self-compacts during a `reInstructAndAwait.execute()` call, `ReInstructAndAwaitImpl` handles session rotation internally and returns a `Responded` outcome. This is not `RejectionNegotiationUseCase`'s concern — it is tested at the `ReInstructAndAwait` layer. Adding a self-compaction test here would be testing `ReInstructAndAwait`'s internals through the wrong abstraction boundary.

**Action taken:** None needed. The concern is addressed by existing `ReInstructAndAwait` tests.

### Suggestion 1: Pair usage in test file — ACKNOWLEDGED, NOT ADDRESSED

Low priority. Test-internal helper with minimal impact. Not worth the churn of extracting a data class for a test fake's call recording.

### Suggestion 2: FailedWorkflow as fourth variant — ACKNOWLEDGED

Correct observation. This is an intentional implementation decision. The spec lines 337/350 call for propagating FailedWorkflow immediately, and a distinct result type is cleaner than folding it into AgentCrashed. No action needed.

## Tests

All 22 tests pass. No test changes needed for this iteration.

## Changes Made

- Added WHY-NOT comment on `buildDoerComplianceMessage` in `RejectionNegotiationUseCase.kt` documenting the spec gap regarding reviewer counter-reasoning and the protocol limitation.

## Status: COMPLETE
