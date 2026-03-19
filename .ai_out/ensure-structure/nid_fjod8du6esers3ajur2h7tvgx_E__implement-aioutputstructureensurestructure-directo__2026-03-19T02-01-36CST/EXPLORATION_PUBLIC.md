# Exploration Summary

## Task
Implement `AiOutputStructure.ensureStructure(parts: List<Part>)` — directory creation for `.ai_out/`.

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt` — Target class, has path resolution methods
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/Part.kt` — Part data class with `name`, `phase`, `subParts`
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPart.kt` — SubPart with `name`, `role`, `agentType`, `model`
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/Phase.kt` — Enum: PLANNING, EXECUTION
- `app/src/test/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructureTest.kt` — Existing tests (path resolution)
- `doc/schema/ai-out-directory.md` — Spec (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)

## Dependencies (both CLOSED)
- nid_9kic96nh6mb8r5legcsvt46uy_E — AiOutputStructure path resolution ✅
- nid_m3cm8xizw5qhu1cu3454rca79_E — Part/SubPart data classes ✅

## Design Context
- AiOutputStructure is pure path resolution (no I/O). `ensureStructure()` adds I/O.
- Planning sub-parts are flat under `planning/`. Execution sub-parts nested under `execution/${part}/${subPart}/`.
- `__feedback/` dirs exist ONLY at execution part level.
- `harness_private/` and `shared/plan/` are always created regardless of workflow type.
- Uses `Files.createDirectories()` for idempotency.
