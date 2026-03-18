---
id: nid_ukrg4wjdjtlcn20jf3m3hrh8a_E
title: "SIMPLIFY_CANDIDATE: Use batch feedback in V1 instead of granular per-item feedback loop"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:46:22Z
status_updated_iso: 2026-03-18T14:46:22Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, feedback-loop]
---

The granular per-item feedback loop (doc/plan/granular-feedback-loop.md) is the single most complex spec in the system.

## Current Complexity
- Per-item feedback processing with severity ordering (critical -> important -> optional)
- Resolution marker parsing (## Resolution: ADDRESSED/REJECTED/SKIPPED)
- Harness-owned file movement between pending/, addressed/, rejected/ directories
- Per-item rejection negotiation with reviewer (1 round max)
- 11 requirements across 6 gates
- RejectionNegotiationUseCase as a sub use case
- Feedback files presence guard + part completion guard
- Nested loop structure (outer: iteration, inner: per-item)

## Proposed Simplification
For V1, use batch feedback: reviewer writes all feedback at once, doer addresses all at once, with a single compaction check at the batch boundary. This delivers ~80% of the value with ~20% of the complexity.

## Why This Improves Robustness
- Fewer moving parts = fewer failure modes
- No file-movement logic that could fail mid-batch
- No per-item negotiation that could deadlock or crash at each step
- Single compaction check is deterministic vs. N compaction opportunities that each add branching

## What We Lose (acceptable for V1)
- Per-item compaction opportunities (but done-boundary compaction still works at batch level)
- Focused per-item context for doer (but batch feedback with clear item numbering is adequate)
- Severity-based early termination (but V1 serial execution makes this less critical)

## Specs Affected
- doc/plan/granular-feedback-loop.md (primary)
- doc/core/PartExecutor.md (simplifies the doer+reviewer iteration flow)
- doc/use-case/ContextWindowSelfCompactionUseCase.md (fewer compaction trigger points)

