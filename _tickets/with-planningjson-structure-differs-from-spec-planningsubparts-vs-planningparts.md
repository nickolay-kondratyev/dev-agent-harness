---
closed_iso: 2026-03-18T15:32:37Z
id: nid_zgw4sjklj66xn06k7lk5h170b_E
title: "with-planning.json structure differs from spec (planningSubParts vs planningParts)"
status: closed
deps: []
links: []
created_iso: 2026-03-18T15:03:04Z
status_updated_iso: 2026-03-18T15:32:37Z
type: bug
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [config, schema]
---

Actual config/workflows/with-planning.json uses:
- "planningSubParts" (flat array of sub-parts)
- "executionPhasesFrom": "plan.json"

Spec in doc/schema/plan-and-current-state.md lines 475-493 documents:
- "planningParts" (array of full part objects with name, phase, description, subParts)
- "executionPhasesFrom": "plan_flow.json"

Also: actual config/workflows/straightforward.json omits the "phase": "execution" field that the spec requires per SubPart schema.

Per doc/high-level.md: "When code diverges from these docs, the docs are correct and the code needs updating." — the config files need updating to match specs.


## Notes

**2026-03-18T15:32:47Z**

## Resolution (2026-03-18)

All three discrepancies between config files and spec (doc/schema/plan-and-current-state.md) have been fixed:

1. **with-planning.json**: Changed `planningSubParts` (flat array of sub-parts) to `planningParts` (array of full part objects containing `name`, `phase`, `description`, `subParts`) — matching the unified parts/sub-parts schema.
2. **with-planning.json**: Changed `executionPhasesFrom: "plan.json"` to `"plan_flow.json"` per spec.
3. **straightforward.json**: Added missing `"phase": "execution"` field to the part object, as required by the Part Fields schema.

Note: The Kotlin instruction code (InstructionText.kt) still tells planners to write `plan.json` rather than `plan_flow.json`. This is a separate concern — the config `executionPhasesFrom` field names the file the harness reads, while the instruction text tells the planner what to write. These should be kept in sync when the planner instruction code is updated.

Tests pass. Commit: e4ad5d1
