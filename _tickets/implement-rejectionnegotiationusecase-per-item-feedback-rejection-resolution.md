---
closed_iso: 2026-03-19T18:18:00Z
id: nid_rnusi51qg9yw7cmszkes0l1ab_E
title: "Implement RejectionNegotiationUseCase — per-item feedback rejection resolution"
status: closed
deps: [nid_dnelaf98097nicijp4kvjfd1d_E, nid_92vpmdxcn3j8f98gzgu9eln43_E]
links: []
created_iso: 2026-03-18T22:30:32Z
status_updated_iso: 2026-03-19T18:18:00Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [granular-feedback-loop, use-case, rejection-negotiation]
---

Implement `RejectionNegotiationUseCase` (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) — a self-contained use case for per-item feedback rejection negotiation.

## Context
Spec: `doc/plan/granular-feedback-loop.md` (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E), section "REJECTION_NEGOTIATION".

When a doer writes `## Resolution: REJECTED` in a feedback file, the harness sends the rejection + reasoning to the reviewer for judgment. Bounded at 1 round — reviewer judges once, judgment is final.

## Flow
1. Harness sends rejection + doer reasoning to reviewer via `ReInstructAndAwait`
2. Reviewer signals `done pass` (accept rejection) or `done needs_iteration` (insist)
3. If reviewer accepts → `RejectionResult.Accepted` → caller moves file to `rejected/`
4. If reviewer insists → doer MUST address immediately (reviewer is authority)
   - Re-instruction to doer via `ReInstructAndAwait` with reviewer counter-reasoning
   - Doer writes `## Resolution: ADDRESSED` (if still REJECTED → `RejectionResult.AgentCrashed`)
   - → `RejectionResult.AddressedAfterInsistence` → caller moves file to `addressed/`

## Interface
```kotlin
sealed class RejectionResult {
    object Accepted : RejectionResult()                        // reviewer accepts rejection
    object AddressedAfterInsistence : RejectionResult()        // reviewer insisted, doer complied
    data class AgentCrashed(val details: String) : RejectionResult()  // doer refused after insistence
    data class FailedWorkflow(val reason: String) : RejectionResult() // agent signaled fail-workflow
}

class RejectionNegotiationUseCase(
    private val reInstructAndAwait: ReInstructAndAwait,
    private val feedbackResolutionParser: FeedbackResolutionParser,
    // ... logging
) {
    suspend fun execute(
        doerHandle: SpawnedAgentHandle,
        reviewerHandle: SpawnedAgentHandle,
        feedbackFilePath: Path,
    ): RejectionResult
}
```

## Dependencies
- `ReInstructAndAwait` (ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E)
- `FeedbackResolutionParser` (for reading updated resolution marker after doer compliance)
- `AgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — via ReInstructAndAwait

## Testing (via FakeAgentFacade + virtual time)
- Unit test: REJECTED → reviewer accepts (pass) → `Accepted`
- Unit test: REJECTED → reviewer insists (needs_iteration) → doer addresses → `AddressedAfterInsistence`
- Unit test: REJECTED → reviewer insists → doer still rejects → `AgentCrashed`
- Unit test: reviewer crashes during judgment → `AgentCrashed`
- Unit test: doer crashes during compliance → `AgentCrashed`
- Unit test: reviewer signals fail-workflow → `FailedWorkflow`

## Package
`com.glassthought.shepherd.usecase`

## Key Properties
- No loop: single round — reviewer judges once, judgment is final
- Reviewer is authority: if reviewer insists, doer must comply
- Uses existing signals: no protocol extension needed
- Self-compaction friendly: each negotiation step is a done boundary
- Fully testable in isolation from PartExecutorImpl

## Acceptance Criteria
- All unit tests pass including all negotiation scenarios
- Clean boundary between this use case and PartExecutorImpl


## Notes

**2026-03-18T22:53:27Z**

Note: RejectionNegotiationUseCase owns its own message templates for reviewer judgment instructions and doer compliance instructions (inline, not via ContextForAgentProvider). The messages are simple, non-reusable templates specific to the negotiation flow. See spec section 'Reviewer Instructions for Rejection Judgment' for the template.
