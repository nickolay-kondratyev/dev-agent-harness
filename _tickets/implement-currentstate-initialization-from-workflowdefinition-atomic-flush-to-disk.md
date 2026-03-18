---
id: nid_o5azwgdl76nnofttpt7ljgkua_E
title: "Implement CurrentState initialization from WorkflowDefinition + atomic flush to disk"
status: open
deps: [nid_m3cm8xizw5qhu1cu3454rca79_E, nid_kavqh23pdfq56cdli0fv4sm3u_E, nid_9kic96nh6mb8r5legcsvt46uy_E]
links: []
created_iso: 2026-03-18T18:03:26Z
status_updated_iso: 2026-03-18T18:03:26Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [plan-current-state, persistence, initialization]
---

Implement CurrentState initialization and persistence from plan-and-current-state spec (ref.ap.56azZbk7lAMll0D4Ot2G0.E, lines 428-452 and 597-622).

## What to implement

### 1. CurrentStateInitializer (spec lines 428-447)
Creates the initial in-memory CurrentState from a WorkflowDefinition.

**Straightforward workflow** (spec line 445-447):
- Creates CurrentState with `parts` (all `phase: "execution"`) directly from WorkflowDefinition.parts
- Adds runtime fields: `status: NOT_STARTED` on every sub-part, `iteration.current: 0` on reviewers

**With-planning workflow** (spec lines 431-434):
- Creates CurrentState with `parts` containing ONE entry: the planning part from `planningParts`
- Adds runtime fields: `status: NOT_STARTED`, `iteration.current: 0` on plan_reviewer
- No execution parts yet — they come later from plan_flow.json

### 2. CurrentStatePersistence — Atomic Flush (spec lines 597-622)
```kotlin
interface CurrentStatePersistence {
    suspend fun flush(state: CurrentState, targetDir: Path)
}
```
- Serializes full CurrentState to JSON via Jackson
- Writes to `harness_private/current_state.json` within the `.ai_out/` directory
- **Atomic write**: write to temp file, then rename (spec line 621: "full file rewrite — atomic write to temp file + rename")
- Called after EVERY mutation (spawn, signal, crash, plan conversion)

### 3. CurrentState Mutation Methods
Add methods or extension functions to CurrentState for safe mutations:
- `updateSubPartStatus(partName, subPartName, newStatus)` — validates transition via SubPartStateTransition
- `incrementIteration(partName, subPartName)` — increments reviewer iteration.current
- `addSessionRecord(partName, subPartName, record)` — appends to sessionIds array
- `appendExecutionParts(parts: List<Part>)` — used after planning converges

Each mutation method MUST call flush after completion. The spec mandates flush after every mutation (line 620).

### 4. Part-Level Derived Status Queries (spec lines 84-87)
Implement query methods on CurrentState for part-level status. Part status is **derived** — no explicit field:
- All sub-parts `COMPLETED` → part complete
- Any sub-part `FAILED` → part failed
- First non-`COMPLETED` sub-part → resume point

### 5. Tests
- Test straightforward init: all sub-parts get NOT_STARTED, reviewers get iteration.current=0
- Test with-planning init: only planning part in parts array, no execution parts
- Test atomic flush: verify file contents match expected JSON
- Test flush atomicity: verify temp file + rename pattern
- Test updateSubPartStatus with valid transitions succeeds
- Test updateSubPartStatus with invalid transitions throws
- Test incrementIteration increments correctly
- Test addSessionRecord appends to array
- Test appendExecutionParts adds to parts list
- Round-trip test: init → flush → read back → verify equality

- Test derived part status: all COMPLETED → part complete, any FAILED → part failed, first non-COMPLETED → resume point

## Out of scope
- Wiring into `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — will be addressed in a separate wiring ticket. This ticket produces the standalone classes + tests.

## Key invariant from spec
"No component reads current_state.json from disk during a run." The disk file is for durability/observability only.

## Package
`com.glassthought.shepherd.core.state`

## Files to read
- `doc/schema/plan-and-current-state.md` — spec lines 428-452, 597-622
- `doc/core/TicketShepherdCreator.md` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — wiring context
- `doc/schema/ai-out-directory.md` (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) — harness_private/ directory


## Notes

**2026-03-18T20:51:14Z**

Added dep on nid_9kic96nh6mb8r5legcsvt46uy_E (AiOutputStructure path resolution). The flush-to-disk logic MUST use AiOutputStructure.currentStateJson() for the output path — NOT compute it independently. This prevents dual path derivation sources.
