# Implementation Complete: AiOutputStructure.ensureStructure()

## What was done

Added `ensureStructure(parts: List<Part>)` method to `AiOutputStructure` that creates the full
directory skeleton under `.ai_out/` for a given list of parts.

### Behavior
- Always creates `harness_private/` and `shared/plan/`
- For PLANNING parts: creates `planning/${subPart}/private/` and `planning/${subPart}/comm/{in,out}/`
- For EXECUTION parts: creates `execution/${part}/__feedback/{pending,addressed,rejected}/` and
  `execution/${part}/${subPart}/private/` and `execution/${part}/${subPart}/comm/{in,out}/`
- Uses `Files.createDirectories()` for idempotency
- Creates directories only, no files

## Files modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt` — added `ensureStructure()` method
- `app/src/test/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructureEnsureStructureTest.kt` — new test file (34 test cases)

## Tests
- 34 test cases, all passing
- Mixed planning + execution: verifies all directory types created
- Negative: verifies `__feedback/` NOT created under planning
- Idempotency: calling twice produces same result
- Planning-only: no execution or `__feedback` dirs created
- Execution-only: no planning dirs created
