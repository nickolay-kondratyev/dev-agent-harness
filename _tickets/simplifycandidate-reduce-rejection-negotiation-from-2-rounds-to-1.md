---
id: nid_zt4yszk5f92t7q1pawy93x6ms_E
title: "SIMPLIFY_CANDIDATE: Reduce rejection negotiation from 2 rounds to 1"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:02:46Z
status_updated_iso: 2026-03-15T01:02:46Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, partexecutor]
---

The PartExecutor is the most complex component in the system. It has three nested loop levels:
  outer iteration loop → inner feedback loop → rejection negotiation loop (2 rounds)

The spec (doc/plan/granular-feedback-loop.md) defines RejectionNegotiationUseCase with 2 disagreement rounds per feedback item. However, the reviewer is already designated as authority. The second round rarely produces a different outcome.

## Proposal
Reduce rejection negotiation to 1 round (reviewer insists once → accepted as authority).

## Benefits
- SIMPLER: Removes an entire nested loop level from the deepest nesting point in the system
- MORE ROBUST: Fewer states to track, fewer race conditions, less context window consumption in the deepest nesting
- The reviewer-as-authority principle is already established — the second round just delays the inevitable

## Affected Specs
- doc/plan/granular-feedback-loop.md (RejectionNegotiationUseCase, D7)
- doc/core/PartExecutor.md (PartExecutorImpl step 4f REJECTION_NEGOTIATION sub-flow)

## Risk
- Low: The doer still gets one chance to push back. Legitimate disagreements are captured. The only thing lost is a second round that almost never changes the outcome.

