---
id: nid_cos4q2sm2k5vznsg4vo5ajpwa_E
title: "SIMPLIFY_CANDIDATE: Merge plan_flow.json and PLAN.md into single planner output"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T15:29:01Z
status_updated_iso: 2026-03-18T15:35:25Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

FEEDBACK:
--------------------------------------------------------------------------------
## Current State
In `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md`, the planner agent produces TWO separate output files:
1. `plan_flow.json` — the structured execution plan
2. `PLAN.md` — human-readable plan description

The harness then reads both files, validates `plan_flow.json`, and uses `PLAN.md` content separately.

## Proposed Simplification
Merge into a SINGLE output file (`plan_flow.json`) with an embedded `planDescription` field containing the human-readable description. The planner writes one file instead of two.

## Why This Is Simpler AND More Robust
- **Fewer failure modes**: Eliminates the case where planner writes one file but forgets the other (partial output).
- **Simpler planner instructions**: Agent only needs to know about one output file, reducing instruction surface area.
- **Atomic output**: Single file = single validation point. No need to check two files exist and are consistent.
- **Less harness parsing code**: One file read instead of two.

## Affected Specs
- `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E)
- `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E)
- `doc/schema/ai-out-directory.md` (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
--------------------------------------------------------------------------------


DECISION: KEEP PLAN.md. Document that PLAN.md will be passed to the DOER agent and doer-reviewer agent so that they know what was planned. While the `plan_flow.json` is used by the harness to orchestrate the flow. They are different responsibilities and it should be clear in the specs.