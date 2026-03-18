---
closed_iso: 2026-03-18T15:27:02Z
id: nid_du35igvdo2740zd0g6m8wa2mz_E
title: "SIMPLIFY_CANDIDATE: Simplify rejection negotiation to reviewer-final-authority — eliminate 3-step bounded protocol"
status: closed
deps: []
links: []
created_iso: 2026-03-18T15:09:28Z
status_updated_iso: 2026-03-18T15:27:02Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, feedback-loop, robustness]
---

## Problem

The granular feedback loop (`doc/plan/granular-feedback-loop.md`) includes `RejectionNegotiationUseCase` — a 3-step bounded negotiation protocol:
1. Doer receives feedback item → writes `## Resolution: REJECTED` with rationale
2. Reviewer judges rejection → either PASS (accept doer reasoning) or INSIST (doer must comply)
3. If INSIST → doer must comply → if doer STILL rejects → **crash** (unrecoverable)

This introduces:
- `RejectionNegotiationUseCase` sub use case class
- Doer pushback guidance in instruction assembly (`doc/core/ContextForAgentProvider.md`)
- A crash-on-double-rejection edge case
- REJECTION_NEGOTIATION pseudocode flow
- Two extra round-trips per rejected item (reviewer judgment + doer retry)

## Proposed Simplification

Reviewer feedback is authoritative. Period.
- Doer receives a feedback item → must address it (ADDRESSED only, no REJECTED/SKIPPED)
- If doer signals done but the item is not properly addressed, reviewer will reject in the next iteration (counting against the iteration budget)
- Remove `RejectionNegotiationUseCase` entirely
- Remove doer pushback guidance from instruction assembly
- Remove `REJECTED`/`SKIPPED` resolution markers (only `ADDRESSED` remains)

## Why This Is Both Simpler AND More Robust
- **Simpler**: Removes an entire sub use case, its pseudocode, the 3-step protocol, and 2 resolution marker variants. The feedback loop becomes: reviewer writes items → doer addresses each → reviewer validates in next pass.
- **More robust**: 
  - Eliminates the crash-on-double-rejection edge case (which is an unrecoverable failure triggered by agent disagreement — not an infrastructure failure)
  - Removes ambiguity about authority. Currently the doer "might" reject and "might" get overridden — unclear who is responsible. With reviewer-as-authority, responsibility is clear.
  - If a doer genuinely cannot address a feedback item (e.g., impossible constraint), it will fail naturally against the iteration budget rather than crashing immediately. This gives the system more graceful degradation.
- **Matches real-world dynamics**: In code review, the reviewer has final authority. Negotiation happens conversationally, not through formal protocol. The structured negotiation adds ceremony without proportional value.

