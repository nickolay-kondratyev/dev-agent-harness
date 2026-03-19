# Implementation: PlanFlowConverter

## What Was Done

Implemented `PlanFlowConverter` — reads `plan_flow.json` (produced by a planning agent), validates it, initializes runtime fields, appends execution parts to in-memory `CurrentState`, ensures `.ai_out/` directory structure, flushes to disk, and deletes the source file.

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverter.kt` | Interface + `PlanFlowConverterImpl` — full conversion pipeline |
| `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt` | Exception for validation failures (empty parts, non-execution phases, malformed JSON) |
| `app/src/test/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverterTest.kt` | 26 BDD tests covering all scenarios |

## Conversion Steps (in order)

1. Read `plan_flow.json` from `AiOutputStructure.planFlowJson()`
2. Deserialize via `ShepherdObjectMapper` into `CurrentState`
3. Validate: at least one part, all parts phase=EXECUTION
4. Initialize runtime fields via `CurrentStateInitializerImpl.initializePart()` (status=NOT_STARTED, iteration.current=0)
5. Append to in-memory `CurrentState` via `appendExecutionParts()`
6. Call `AiOutputStructure.ensureStructure()` for new parts
7. Flush `CurrentState` to disk via `CurrentStatePersistence.flush()`
8. Delete `plan_flow.json`
9. Return initialized execution parts

## Test Coverage (26 tests)

- Valid conversion: part structure, phase, subParts count, runtime field initialization
- Append semantics: planning part at index 0 preserved, execution parts appended after
- Multiple execution parts
- Runtime fields in plan_flow.json silently overwritten by initializePart()
- Empty parts array -> PlanConversionException
- Planning-phase parts -> PlanConversionException
- Mixed phases -> PlanConversionException
- Malformed JSON -> PlanConversionException
- plan_flow.json deleted after conversion
- current_state.json flushed to disk
- Directory structure created (execution subdirs, feedback dirs)

## Anchor Point

- `ap.bV7kMn3pQ9wRxYz2LfJ8s.E` — PlanFlowConverter interface

## Design Decisions

- `PlanConversionException` extends `RuntimeException` (not `AsgardBaseException`) because `AsgardBaseException` is not available in the current asgardCore jar. When it becomes available, this should be migrated.
- Constructor injection: `AiOutputStructure`, `CurrentStatePersistence`, `OutFactory`
- Uses `CurrentStateInitializerImpl.initializePart()` companion object method for runtime field initialization (reuse, not duplication)
