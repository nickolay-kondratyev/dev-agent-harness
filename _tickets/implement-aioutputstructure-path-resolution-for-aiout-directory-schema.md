---
id: nid_9kic96nh6mb8r5legcsvt46uy_E
title: "Implement AiOutputStructure — path resolution for .ai_out/ directory schema"
status: open
deps: []
links: []
created_iso: 2026-03-18T20:44:30Z
status_updated_iso: 2026-03-18T20:44:30Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ai-out, core]
---

Rebuild AiOutputStructure class per the new schema in doc/schema/ai-out-directory.md (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

The previous implementation was deleted (commit 072fa28) for clean rebuild against the new schema.

## What to Build

A class `AiOutputStructure` (package `com.glassthought.shepherd.core.filestructure`) that provides **path resolution** for every element in the `.ai_out/` directory tree.

### Constructor
- Takes `repoRoot: Path` (the git repo root)
- Takes `branch: String` (the git branch name — used as the top-level subdirectory)

### Path Resolution Methods (all return `Path`)
- `branchRoot()` → `.ai_out/${branch}/`
- `harnessPrivateDir()` → `.ai_out/${branch}/harness_private/`
- `currentStateJson()` → `.ai_out/${branch}/harness_private/current_state.json`
- `planFlowJson()` → `.ai_out/${branch}/harness_private/plan_flow.json`
- `sharedPlanDir()` → `.ai_out/${branch}/shared/plan/`
- `planMd()` → `.ai_out/${branch}/shared/plan/PLAN.md`
- `planningSubPartDir(subPartName)` → `.ai_out/${branch}/planning/${subPartName}/`
- `executionPartDir(partName)` → `.ai_out/${branch}/execution/${partName}/`
- `executionSubPartDir(partName, subPartName)` → `.ai_out/${branch}/execution/${partName}/${subPartName}/`
- `feedbackDir(partName)` → `.ai_out/${branch}/execution/${partName}/__feedback/`
- `feedbackPendingDir(partName)` → `.../__feedback/pending/`
- `feedbackAddressedDir(partName)` → `.../__feedback/addressed/`
- `feedbackRejectedDir(partName)` → `.../__feedback/rejected/`
- Phase-aware sub-part methods — **planning sub-parts have NO part-level grouping; execution sub-parts DO**:
  - Planning: `planningSubPartPrivateDir(subPartName)` → `.ai_out/${branch}/planning/${subPartName}/private/`
  - Planning: `planningPrivateMd(subPartName)` → `.ai_out/${branch}/planning/${subPartName}/private/PRIVATE.md`
  - Planning: `planningCommInDir(subPartName)` → `.ai_out/${branch}/planning/${subPartName}/comm/in/`
  - Planning: `planningCommOutDir(subPartName)` → `.ai_out/${branch}/planning/${subPartName}/comm/out/`
  - Planning: `planningInstructionsMd(subPartName)` → `.ai_out/${branch}/planning/${subPartName}/comm/in/instructions.md`
  - Planning: `planningPublicMd(subPartName)` → `.ai_out/${branch}/planning/${subPartName}/comm/out/PUBLIC.md`
  - Execution: `executionSubPartPrivateDir(partName, subPartName)` → `.ai_out/${branch}/execution/${partName}/${subPartName}/private/`
  - Execution: `executionPrivateMd(partName, subPartName)` → `.ai_out/${branch}/execution/${partName}/${subPartName}/private/PRIVATE.md`
  - Execution: `executionCommInDir(partName, subPartName)` → `.ai_out/${branch}/execution/${partName}/${subPartName}/comm/in/`
  - Execution: `executionCommOutDir(partName, subPartName)` → `.ai_out/${branch}/execution/${partName}/${subPartName}/comm/out/`
  - Execution: `executionInstructionsMd(partName, subPartName)` → `.ai_out/${branch}/execution/${partName}/${subPartName}/comm/in/instructions.md`
  - Execution: `executionPublicMd(partName, subPartName)` → `.ai_out/${branch}/execution/${partName}/${subPartName}/comm/out/PUBLIC.md`

### Design Notes
- Use **separate methods per phase** (e.g., `planningPublicMd(subPartName)` vs `executionPublicMd(partName, subPartName)`) rather than a phase parameter with nullable `partName`. This gives compile-time safety — you can't accidentally pass null partName for execution or a partName for planning.
- __feedback/ directories exist only at execution part level — NOT planning.
- Path resolution is pure computation (no I/O). Keep it separate from directory creation.

### Tests
- Unit test every path resolution method.
- Verify branch names with special characters (slashes, underscores) are handled correctly.
- Concrete planning vs execution test cases:
  - `planningPublicMd("plan")` → `.ai_out/my_branch/planning/plan/comm/out/PUBLIC.md`
  - `executionPublicMd("backend", "impl")` → `.ai_out/my_branch/execution/backend/impl/comm/out/PUBLIC.md`
  - Verify these two paths are structurally different (planning has no part-level grouping).

### Spec Reference
- Directory tree: doc/schema/ai-out-directory.md (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
- "Codified In" section at bottom of spec confirms this needs rebuilding.


## Notes

**2026-03-18T21:10:45Z**

Branch name with slashes (e.g., feature/my-ticket): Path.resolve() handles this natively — slashes create nested directories. This is the correct behavior since .ai_out/ is a local directory tree, and Files.createDirectories() will create intermediate dirs. Tests should verify this works correctly with slashes in branch names.

**2026-03-18T21:29:27Z**

Test clarification: add explicit test case for branch names with slashes. E.g., with branch='feature/my-ticket', planningPublicMd("plan") should resolve to .ai_out/feature/my-ticket/planning/plan/comm/out/PUBLIC.md. This converts the existing note about slash behavior into a concrete test assertion.
