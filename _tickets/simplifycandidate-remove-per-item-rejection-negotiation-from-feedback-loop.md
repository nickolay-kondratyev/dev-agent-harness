---
closed_iso: 2026-03-18T15:00:37Z
id: nid_7rwask6soqyhgdvrg6tr0nyyy_E
title: "SIMPLIFY_CANDIDATE: Remove per-item rejection negotiation from feedback loop"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:46:44Z
status_updated_iso: 2026-03-18T15:00:37Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, feedback-loop]
---

The per-item rejection negotiation in the feedback loop (doc/plan/granular-feedback-loop.md) adds disproportionate complexity for questionable value.

## Current Complexity
When doer disagrees with a feedback item:
1. Doer writes resolution marker with REJECTED + rationale
2. Harness detects rejection, invokes RejectionNegotiationUseCase
3. Reviewer judges whether rejection is valid (1 round)
4. If reviewer insists: doer MUST comply, re-instruct with reviewer judgment
5. If doer still rejects after insistence: AgentCrashed

This is a 4-level-deep chain PER REJECTED ITEM. For N items with rejections, worst case is 3N messages.

## Proposed Simplification
Reviewer is authority, period. Doer MUST address all feedback. If doer believes an item is wrong, it can note disagreement in the resolution marker but must still address the item. No negotiation round-trip.

## Why This Improves Robustness
- Eliminates entire RejectionNegotiationUseCase class and its failure modes
- Removes the 4-level crash chain that could fail at each step
- Removes reviewer re-judgment invocation (extra agent interaction with its own timeout/health risks)
- Deterministic outcome: all feedback items get addressed, always
- Simpler contract for agents (MUST address) vs. complex contract (MAY reject, but only if reviewer agrees)

## What We Lose (acceptable)
- Doer cannot veto reviewer feedback. But: (a) reviewer already has context of the implementation, (b) if reviewer is consistently wrong, the problem is the reviewer prompt, not the negotiation mechanism, (c) doer can still flag disagreement in resolution notes for human review.

## Specs Affected
- doc/plan/granular-feedback-loop.md (primary - remove rejection negotiation requirements)
- doc/core/PartExecutor.md (simplifies per-item processing in the inner loop)

## Note
This is independent from the batch-vs-granular feedback decision. Even if granular per-item processing is kept, rejection negotiation can still be removed.

