# AiOutputStructure — Path Resolution Implementation

## What Was Done

Implemented `AiOutputStructure` class providing pure path resolution for every element in the `.ai_out/` directory tree, per the spec at ref.ap.BXQlLDTec7cVVOrzXWfR7.E.

## Key Design Decisions

- **Pure computation, no I/O** — all methods return `Path` without touching the filesystem.
- **Separate methods per phase** — `planningPublicMd(subPartName)` vs `executionPublicMd(partName, subPartName)` for compile-time safety. Planning has no part-level grouping; execution does.
- **`__feedback/` at execution part level only** — not at sub-part level, not in planning.
- **Uses "pending"** directory name per spec (not "unaddressed" from ProtocolVocabulary — separate cleanup ticket).
- **All path segments as named constants** in companion object — no magic strings.
- **TooManyFunctions detekt suppression** added to baseline — intentional design with 25 methods mapping 1:1 to directory tree elements.

## Files

- `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt` — implementation
- `app/src/test/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructureTest.kt` — 27 test cases
- `detekt-baseline.xml` — added TooManyFunctions suppression for AiOutputStructure

## Tests

- 27 unit tests covering every path resolution method
- Branch with slashes (`feature/my-ticket`) verified
- Planning vs execution structural difference verified
- Feedback dirs verified at part level
- All tests GREEN (`./gradlew :app:test` passes)
