# Implementation: AiOutputStructure

## Summary

Implemented `AiOutputStructure` class that manages path resolution and directory creation for the `.ai_out/${branch}/` directory tree used by the Chainsaw agent harness.

## Files Created

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructure.kt` | Main class + `Part` data class |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructureTest.kt` | BDD unit tests (32 test cases) |

## Files Modified

| File | Change |
|------|--------|
| `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` | Added anchor point `ap.XBNUQHLjDLpAr8F9IOyXU.E` below `## File Structure` heading |

## API Surface

### `AiOutputStructure(repoRoot: Path)`
- Fail-fast constructor: throws `IllegalArgumentException` if `repoRoot` does not exist.

### Path Resolution Methods (pure, no I/O)
| Method | Returns |
|--------|---------|
| `harnessPrivateDir(branch)` | `.ai_out/${branch}/harness_private` |
| `sharedDir(branch)` | `.ai_out/${branch}/shared` |
| `planDir(branch)` | `.ai_out/${branch}/shared/plan` |
| `planningRoleDir(branch, role)` | `.ai_out/${branch}/planning/${role}` |
| `planningPublicMd(branch, role)` | `.ai_out/${branch}/planning/${role}/PUBLIC.md` |
| `planningPrivateMd(branch, role)` | `.ai_out/${branch}/planning/${role}/PRIVATE.md` |
| `planningSessionIdsDir(branch, role)` | `.ai_out/${branch}/planning/${role}/session_ids` |
| `phaseRoleDir(branch, part, role)` | `.ai_out/${branch}/phases/${part}/${role}` |
| `sessionIdsDir(branch, part, role)` | `.ai_out/${branch}/phases/${part}/${role}/session_ids` |
| `publicMd(branch, part, role)` | `.ai_out/${branch}/phases/${part}/${role}/PUBLIC.md` |
| `privateMd(branch, part, role)` | `.ai_out/${branch}/phases/${part}/${role}/PRIVATE.md` |
| `sharedContextMd(branch)` | `.ai_out/${branch}/shared/SHARED_CONTEXT.md` |
| `locationsFile(branch)` | `.ai_out/${branch}/shared/LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt` |

### Directory Creation (I/O)
| Method | Behavior |
|--------|----------|
| `ensureStructure(branch, parts, planningRoles)` | Creates full directory tree. Idempotent. `planningRoles` defaults to `emptyList()`. |

## Reviewer Feedback Incorporated

1. **Planning role path methods**: Added `planningPublicMd`, `planningPrivateMd`, `planningSessionIdsDir` as requested.
2. **Extended `ensureStructure`**: Added `planningRoles: List<String> = emptyList()` parameter. When non-empty, creates planning role directories and their `session_ids/` subdirectories.

## Design Decisions

- **No interface**: YAGNI. Extract later if polymorphism is needed.
- **No OutFactory/logging**: Pure utility class. Path computation has no logging value.
- **No suspend**: Local filesystem I/O only. No coroutine overhead warranted.
- **Companion object constants**: All directory/file names are named constants, no magic strings.
- **`Part` data class co-located**: Simple value type tightly coupled to `ensureStructure` API.

## Anchor Point
- `ap.XBNUQHLjDLpAr8F9IOyXU.E` — placed in design ticket `## File Structure` heading and referenced in `AiOutputStructure` KDoc.

## Verification
- `./gradlew :app:test` — all tests pass (including 32 new AiOutputStructureTest cases)
- `./gradlew :app:build` — full build passes
- No changes to `app/build.gradle.kts`
