---
closed_iso: 2026-03-18T13:46:31Z
id: nid_nabagnd7enkuyunsrbzaljeol_E
title: "SIMPLIFY_CANDIDATE: Keep plan_flow.json after conversion — remove deletion step"
status: closed
deps: []
links: []
created_iso: 2026-03-17T23:53:45Z
status_updated_iso: 2026-03-18T13:46:31Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec-change]
---

In `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) step 5 of `convertPlanToExecutionParts`, the harness deletes `plan_flow.json` after converting it to `current_state.json`.

**Problem**: The deletion step:
- Adds a file-system operation that could fail (permissions, file locks) in the middle of a critical conversion flow
- Destroys the planner's original output — useful for debugging when execution goes wrong\n- Adds logic to handle "what if deletion fails?" (currently unspecified — likely silent failure)\n\n**Simplification**: Don't delete `plan_flow.json`. Leave it as a read-only artifact alongside `current_state.json`. Both are in `harness_private/` and git-tracked.\n\n**What changes**:\n- `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` — remove step "Delete plan_flow.json" from convertPlanToExecutionParts\n- `doc/schema/plan-and-current-state.md` — update lifecycle description: plan_flow.json is kept as audit artifact after conversion, not deleted\n- `doc/schema/ai-out-directory.md` — update plan_flow.json description: kept after conversion as read-only audit trail\n\n**Robustness improvement**: Removes a file-system operation that could fail mid-conversion. Preserves the planner's original output for post-mortem debugging when execution fails — currently this artifact is destroyed and cannot be inspected.\n\n**current_state.json remains the single source of truth** for execution — plan_flow.json is just a preserved historical artifact. No ambiguity about which file drives execution.

