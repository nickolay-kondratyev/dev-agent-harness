---
id: nid_cfhhx7k6cid7dugz6wv760c7p_E
title: "SIMPLIFY_CANDIDATE: Unify planningPart into parts array — eliminate dual-path handling in current_state.json"
status: open
deps: []
links: []
created_iso: 2026-03-17T23:14:06Z
status_updated_iso: 2026-03-17T23:14:06Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

FEEDBACK:
--------------------------------------------------------------------------------
In doc/schema/plan-and-current-state.md, `current_state.json` has two separate locations for parts:
- `planningPart` (singular, top-level field) for the planning phase
- `parts` (array) for execution phases

Both use the **exact same schema** — same sub-part fields, same SubPartStatus transitions, same iteration counter, same sessionIds array. The spec says: "The only differences are: It lives at planningPart (singular) instead of in the parts array."

**Problem:** This creates dual handling throughout the codebase:
1. Code that reads/writes current_state.json must handle both `planningPart` and `parts[]` separately
2. PartExecutor writes to current_state.json "identically regardless of whether it is running the planning phase or an execution part" — yet the JSON location differs
3. Two code paths for serialization/deserialization of the same schema
4. The planning→execution lifecycle (planningPart stays as historical record, parts populated from plan_flow.json) has special-case logic

**Proposed simplification:** Make planning a regular part in the `parts` array. Use a `phase` field or naming convention to distinguish it:
```json
{
  "parts": [
    { "name": "planning", "phase": "planning", ... },
    { "name": "ui_design", "phase": "execution", ... },
    { "name": "backend_impl", "phase": "execution", ... }
  ]
}
```

**Why this works just as well or better:**
- Planning is already treated identically to execution parts for persistence, status transitions, and session tracking
- The planning part is already processed by the same PartExecutor
- After plan conversion, the planning part stays as a historical record — it just stays at index 0 of the array
- V2 resume reads parts the same way regardless of planning/execution — one code path

**Robustness improvement:**
- Single code path for reading/writing all parts — eliminates the risk of planningPart and parts[] handling diverging
- Simpler serialization — one array, not a nullable field + array
- TicketShepherdCreator creates one unified structure instead of two
- convertPlanToExecutionParts appends to existing array instead of populating a separate field

Affected specs:
- doc/schema/plan-and-current-state.md (planningPart field, lifecycle description, examples)
- doc/core/TicketShepherdCreator.md (workflow init)
- doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md (plan conversion)
--------------------------------------------------------------------------------

YES let's simplify BUT also KEEP in mind that planning is special in that it will be called before we even have a plan. Hence, keep that in mind throughout the spec.