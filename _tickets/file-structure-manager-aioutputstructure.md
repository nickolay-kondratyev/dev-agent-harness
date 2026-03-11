---
closed_iso: 2026-03-09T23:49:38Z
id: nid_u7l156vka309r8y1rmr7z1pt7_E
title: "File Structure Manager (AiOutputStructure)"
status: closed
deps: []
links: []
created_iso: 2026-03-09T23:06:35Z
status_updated_iso: 2026-03-09T23:49:38Z
type: feature
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [wave1, filestructure]
---

Implement path resolution and directory creation for the .ai_out/${branch}/ directory layout.

## Scope
- Create `AiOutputStructure` class that manages the `.ai_out/${branch}/` directory tree
- Path resolution methods:
  - `harnessPrivateDir(branch): Path` → `.ai_out/${branch}/harness_private/`
  - `sharedDir(branch): Path` → `.ai_out/${branch}/shared/`
  - `planDir(branch): Path` → `.ai_out/${branch}/shared/plan/`
  - `planningRoleDir(branch, role): Path` → `.ai_out/${branch}/planning/${ROLE}/`
  - `phaseRoleDir(branch, part, role): Path` → `.ai_out/${branch}/phases/${part}/${ROLE}/`
  - `sessionIdsDir(branch, part, role): Path` → `.ai_out/${branch}/phases/${part}/${ROLE}/session_ids/`
  - `publicMd(branch, part, role): Path`, `privateMd(...)`, `sharedContextMd(branch): Path`
  - `locationsFile(branch): Path` → `.ai_out/${branch}/shared/LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt`
- Directory creation: `ensureStructure(branch, parts)` creates all needed directories upfront
- Package: `com.glassthought.shepherd.core.filestructure`

## Key Decisions
- All paths are relative to the repository root (takes root as constructor parameter)
- Fail-fast if repository root does not exist
- Directory creation is idempotent (safe to call multiple times)
- No new dependencies needed — pure Kotlin stdlib (java.nio.file)

## Testing
- Unit tests using temp directories
- Test: path resolution returns correct paths for given branch/part/role
- Test: ensureStructure creates all expected directories
- Test: ensureStructure is idempotent

## Files touched
- New files under `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/`
- New files under `app/src/test/kotlin/com/glassthought/shepherd/core/filestructure/`
- Does NOT touch `app/build.gradle.kts`

## Reference
- See "File Structure" section in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`

## Completion Criteria — Anchor Point
As part of closing this ticket:
1. Run `anchor_point.create` to generate a new AP for this component.
2. Add `ap.XXX.E` just below the `## File Structure` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`.
3. Add `ref.ap.XXX.E` in the KDoc of the `AiOutputStructure` class pointing back to that design ticket section.

## Resolution
**Status: COMPLETED**

Implemented `AiOutputStructure` class with:
- 13 pure path resolution methods (branch, shared, plan, planning role, phase role, session IDs, public/private MD, shared context, locations file)
- `ensureStructure(branch, parts, planningRoles)` for idempotent directory creation
- `Part` data class co-located for structured parts definition
- Fail-fast constructor (`Files.isDirectory` check) + blank string validation on all parameters
- Anchor point: `ref.ap.XBNUQHLjDLpAr8F9IOyXU.E` linked in design ticket and `AiOutputStructure` KDoc
- 43 BDD unit tests (AsgardDescribeSpec) covering all methods, edge cases, idempotency
- No changes to `build.gradle.kts` — pure Kotlin stdlib

### Files Created
- `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructureTest.kt`

