---
id: nid_zgw4sjklj66xn06k7lk5h170b_E
title: "with-planning.json structure differs from spec (planningSubParts vs planningParts)"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T15:03:04Z
status_updated_iso: 2026-03-18T15:27:43Z
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

