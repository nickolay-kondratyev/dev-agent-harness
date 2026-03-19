---
closed_iso: 2026-03-19T14:40:04Z
id: nid_wppjbc4te6exn13bo3o0jln6n_E
title: "Implement plan_flow.json parsing + conversion to CurrentState execution parts"
status: closed
deps: [nid_o5azwgdl76nnofttpt7ljgkua_E]
links: []
created_iso: 2026-03-18T18:03:50Z
status_updated_iso: 2026-03-19T14:40:04Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [plan-current-state, plan-conversion]
---

Implement the plan conversion logic from plan-and-current-state spec (ref.ap.56azZbk7lAMll0D4Ot2G0.E, lines 428-452).

## What to implement

### 1. Parse plan_flow.json — REUSE WorkflowParser (spec line 33: "One parser handles everything")
The spec explicitly states the unified parts/sub-parts schema means one parser. **Do NOT create a separate `PlanFlowParser`**. Instead, reuse the `WorkflowParser` from ticket `nid_kavqh23pdfq56cdli0fv4sm3u_E` (or extend it with a method for parsing raw parts arrays).

`plan_flow.json` has the same structure as a workflow's `parts` array — just execution parts without a top-level `name` or `planningParts`. The parser should deserialize into `List<Part>` using the same Jackson config.

Post-parse validation specific to plan_flow.json:
- All parts must have `phase: "execution"` (no planning parts)
- All sub-parts must have required fields (name, role, agentType, model)
- Runtime fields (status, sessionIds) should be absent — if present, silently ignore per Jackson `FAIL_ON_UNKNOWN_PROPERTIES = false` config (these are added by the harness, not rejected)
- Fail-fast on missing required fields

### 2. Plan Conversion Logic (spec lines 440-444)
After planning converges (PLAN_REVIEWER passes):
1. Parse `harness_private/plan_flow.json`
2. Add runtime fields to each execution sub-part: `status: NOT_STARTED`, `iteration.current: 0` on reviewers
3. **Append** execution parts to the in-memory CurrentState.parts array (planning part stays at index 0)
4. Flush CurrentState to `current_state.json`
5. **Delete** `plan_flow.json` from disk (spec line 444: "deletes plan_flow.json")

This is the `convertPlanToExecutionParts()` function referenced in the spec mutation table (line 617).

### 3. Tests
- Parse valid plan_flow.json (use spec example lines 268-295) — verify parts structure
- Verify runtime fields are added correctly (status=NOT_STARTED, iteration.current=0)
- Verify execution parts are APPENDED (planning part at index 0 preserved)
- Verify plan_flow.json is deleted after conversion
- Test fail-fast on malformed plan_flow.json
- Test fail-fast on plan_flow.json with planning-phase parts (only execution allowed)
- Test fail-fast on missing required sub-part fields
- Test plan_flow.json with runtime fields present is silently ignored (Jackson FAIL_ON_UNKNOWN_PROPERTIES=false)

## Package
`com.glassthought.shepherd.core.state`

## Files to read
- `doc/schema/plan-and-current-state.md` — spec lines 268-295 (plan_flow.json example), 428-452 (lifecycle)
- `doc/use-case/SetupPlanUseCase/__this.md` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) — routing context
- `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) — planning flow


## Notes

**2026-03-18T22:01:00Z**

IMPORTANT — ensureStructure() for execution directories: After appending execution parts to CurrentState (step 3), call AiOutputStructure.ensureStructure() for the newly-added execution parts. At initial setup (TicketShepherdCreator), only planning directories are created for with-planning workflows because execution parts don't exist yet. The plan conversion step is responsible for creating the .ai_out/ directory tree for execution parts (per doc/schema/ai-out-directory.md ref.ap.BXQlLDTec7cVVOrzXWfR7.E). This requires AiOutputStructure as a dependency of the plan conversion logic. Ref: nid_fjod8du6esers3ajur2h7tvgx_E (ensureStructure), nid_9kic96nh6mb8r5legcsvt46uy_E (path resolution).
