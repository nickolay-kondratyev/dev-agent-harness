---
id: nid_fjod8du6esers3ajur2h7tvgx_E
title: "Implement AiOutputStructure.ensureStructure() — directory creation for .ai_out/"
status: open
deps: [nid_9kic96nh6mb8r5legcsvt46uy_E, nid_m3cm8xizw5qhu1cu3454rca79_E]
links: []
created_iso: 2026-03-18T20:44:44Z
status_updated_iso: 2026-03-18T20:44:44Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ai-out, core]
---

Add `ensureStructure()` method to `AiOutputStructure` that creates the full `.ai_out/${branch}/` directory tree.

## What to Build

A method `ensureStructure(parts: List<Part>)` on `AiOutputStructure` that creates all directories for a given workflow configuration.

### Behavior
1. Creates `harness_private/` directory
2. Creates `shared/plan/` directory
3. For each planning sub-part: creates `planning/${subPartName}/private/` and `planning/${subPartName}/comm/{in,out}/`
4. For each execution part:
   - Creates `execution/${partName}/__feedback/{pending,addressed,rejected}/`
   - For each sub-part within the part: creates `execution/${partName}/${subPartName}/private/` and `execution/${partName}/${subPartName}/comm/{in,out}/`
5. Uses `Files.createDirectories()` — idempotent, safe to call multiple times.

### Input Type
Uses the `Part`/`SubPart` data classes from ticket nid_m3cm8xizw5qhu1cu3454rca79_E (dependency). Do NOT duplicate these types locally — wait for that ticket to complete first.

### What It Does NOT Create
- Does NOT create files (`PRIVATE.md`, `PUBLIC.md`, `instructions.md`, `current_state.json`, `plan_flow.json`, `PLAN.md`) — these are created at runtime by agents or the harness.
- Only creates the directory skeleton.

### Tests
- Verify all expected directories are created for a sample workflow with planning + execution parts.
- Verify `__feedback/` directories (pending, addressed, rejected) are created only for execution parts.
- Verify idempotency — calling ensureStructure() twice produces same result.
- Verify planning sub-parts have correct structure (no __feedback/).

### Spec Reference
- doc/schema/ai-out-directory.md (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
- "Initial Creation" section: TicketShepherdCreator calls this as part of ticket setup.


## Notes

**2026-03-18T21:20:15Z**

Phase filtering: When iterating `parts: List<Part>`, filter by `phase == Phase.PLANNING` for the `planning/` tree and `phase == Phase.EXECUTION` for the `execution/` tree. Do NOT create planning directories for execution parts or vice versa.
