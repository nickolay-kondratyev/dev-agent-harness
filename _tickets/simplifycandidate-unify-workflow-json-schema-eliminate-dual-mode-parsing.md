---
id: nid_r6e891iw9scxjk3d10y7dwlt7_E
title: "SIMPLIFY_CANDIDATE: Unify workflow JSON schema — eliminate dual-mode parsing"
status: open
deps: []
links: []
created_iso: 2026-03-18T15:29:12Z
status_updated_iso: 2026-03-18T15:29:12Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

## Current State
In `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E), WorkflowDefinition supports two mutually exclusive modes:
- **Straightforward**: uses `parts` array directly
- **With-planning**: uses `planningParts` + `executionPhasesFrom` (string reference)

This dual-mode schema forces the harness to implement two different parsing paths and handle the `executionPhasesFrom` indirection (resolving the string reference to actual parts).

## Proposed Simplification
Unify to a single `parts` array for ALL workflow modes. For with-planning workflows, the planning part is simply a part with `phase: "planning"` (or a distinct sub-part role). `SetupPlanUseCase` routes based on whether any part has the planning phase marker, not on which top-level field is present.

## Why This Is Simpler AND More Robust
- **One parser, one schema**: Eliminates the branching logic that checks which fields are present.
- **No string-reference resolution**: Removes `executionPhasesFrom` indirection that could fail if the reference is wrong.
- **Fewer edge cases**: No possibility of accidentally providing both `parts` AND `planningParts` (currently an implicit error case).
- **Consistent iteration**: TicketShepherd always iterates over `parts` regardless of workflow mode.

## Affected Specs
- `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E)
- `doc/use-case/SetupPlanUseCase/__this.md` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E)
- `doc/core/TicketShepherdCreator.md` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)

