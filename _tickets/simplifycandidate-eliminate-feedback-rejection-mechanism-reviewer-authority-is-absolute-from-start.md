---
closed_iso: 2026-03-18T13:09:34Z
id: nid_7izsgole14n1hgrnevsw0lqyi_E
title: "SIMPLIFY_CANDIDATE: Eliminate feedback rejection mechanism — reviewer authority is absolute from start"
status: closed
deps: []
links: []
created_iso: 2026-03-18T02:22:27Z
status_updated_iso: 2026-03-18T13:09:34Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, granular-feedback, robustness]
---

## Problem

The granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) includes a RejectionNegotiationUseCase (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) where:
1. Doer writes `## Resolution: REJECTED` marker on a feedback item
2. Harness moves file to `rejected/` directory
3. RejectionNegotiationUseCase re-instructs doer to comply (1 round)
4. If doer still won't comply → AgentCrashed

This is an elaborate mechanism that ultimately enforces reviewer authority anyway — the doer MUST comply or crash.

## Proposed Simplification

Eliminate the REJECTED status entirely:
- Doer must address all `critical__*` and `important__*` feedback (or signal `fail-workflow` if genuinely impossible)
- Doer may SKIP `optional__*` feedback (existing mechanism)
- No `rejected/` directory needed
- No RejectionNegotiationUseCase needed
- No rejection marker parsing needed

## What Gets Removed
- `RejectionNegotiationUseCase` class
- `rejected/` feedback directory (down from 3 dirs to 2: `pending/`, `addressed/`)
- REJECTED marker parsing in feedback file resolution
- Rejection negotiation round logic in PartExecutorImpl inner loop
- Doer pushback guidance in instruction assembly

## Why This Is Also MORE Robust
- Reviewer authority is unambiguous from the start — no "negotiate then force" pattern
- Eliminates an ambiguous intermediate state (rejected-but-pending-negotiation)
- Doer still has escape hatch: `fail-workflow` signal for genuinely impossible requests
- Simpler state machine = fewer edge cases in the feedback loop

## Specs Affected
- `doc/plan/granular-feedback-loop.md` (primary)
- `doc/core/ContextForAgentProvider.md` (remove doer pushback guidance)
- `doc/schema/ai-out-directory.md` (remove `rejected/` directory)

