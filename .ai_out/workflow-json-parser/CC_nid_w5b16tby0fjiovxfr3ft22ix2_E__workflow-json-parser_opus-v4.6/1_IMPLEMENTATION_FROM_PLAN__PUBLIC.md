# Implementation Summary: Workflow JSON Parser

## What Was Built

A complete JSON workflow definition parser that reads workflow JSON files and produces a Kotlin domain model. This enables the Chainsaw harness to load workflow definitions from `config/workflows/`.

## Files Created

### Production Code

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowDefinition.kt` | Domain model: `WorkflowDefinition`, `Part`, `Phase`, `IterationConfig` (ap.MyWV0mG6ZU8XaQOyo14l4.E) |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParser.kt` | `WorkflowParser` interface + `WorkflowParserImpl` (ap.U5oDohccLN3tugPzK9TJa.E) |
| `config/workflows/straightforward.json` | Production straightforward workflow definition |
| `config/workflows/with-planning.json` | Production with-planning workflow definition |

### Test Code

| File | Purpose |
|------|---------|
| `app/src/test/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParserTest.kt` | BDD test class with 8 GIVEN scenarios and 26 assertions |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/straightforward.json` | Test fixture: straightforward workflow |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/with-planning.json` | Test fixture: with-planning workflow |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/multi-part.json` | Test fixture: multiple parts |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/missing-name.json` | Test fixture: missing required name field |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/malformed.json` | Test fixture: invalid JSON syntax |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/empty-phases.json` | Test fixture: part with empty phases |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/neither-parts-nor-planning.json` | Test fixture: neither workflow type |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/both-parts-and-planning.json` | Test fixture: both workflow types (invalid) |

### Modified Files

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Added `jackson-databind:2.17.2` and `jackson-module-kotlin:2.17.2` dependencies |

## Reviewer Feedback Incorporated

1. **"Both parts AND planning fields present" test case**: Added `both-parts-and-planning.json` test fixture and two test assertions (throws `IllegalArgumentException`, message contains "not both"). This validates mutual exclusivity from both directions.

2. **`NoSuchFileException` catch approach**: The implementation lets `NoSuchFileException` propagate naturally from `path.readText()` (called via `withContext(Dispatchers.IO)`), consistent with `TicketParser` pattern. No explicit `exists()` check needed, avoiding the TOCTOU race condition.

## Key Design Decisions

### Pattern Consistency with TicketParser
- Interface + companion factory (`WorkflowParser.standard(outFactory)`)
- `OutFactory` injection, structured logging with `Val`/`ValType`
- `suspend fun parse(path: Path)` with `Dispatchers.IO` for file reads
- Fail-fast on errors

### Jackson Configuration
- `KotlinModule` for data class deserialization via constructor parameters
- `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES = true` to catch missing `max` in `IterationConfig`
- No `@JsonIgnoreProperties(ignoreUnknown = true)` -- strict by default for config files
- Single `ObjectMapper` instance (thread-safe, expensive to create)

### Post-Deserialization Validation
Jackson handles syntactic validation (required non-nullable fields). Additional semantic validation:
- Name must not be blank
- Mutual exclusivity: must have EITHER `parts` OR `planningPhases`, not both, not neither
- Parts must have non-empty phases
- Planning phases must not be empty
- `planningIteration` and `executionPhasesFrom` required when `planningPhases` present

### No Sealed Class for Workflow Types
As specified in the design doc, nullable fields differentiate straightforward vs with-planning workflows. Simple and sufficient.

## Test Coverage

| Scenario | Assertions |
|----------|-----------|
| Straightforward workflow (happy path) | 11: name, parts count, part name/description, phases count/roles, iteration max, null planning fields |
| With-planning workflow (happy path) | 7: name, null parts, planning phases count/roles, iteration max, executionPhasesFrom |
| Multi-part workflow | 5: parts count, second part name/phases/role/iteration |
| File not found | 1: throws NoSuchFileException |
| Malformed JSON | 1: throws JsonProcessingException |
| Missing name field | 1: throws JsonProcessingException (from Jackson Kotlin module) |
| Empty phases in part | 2: throws IllegalArgumentException, message mentions part name |
| Neither parts nor planning | 2: throws IllegalArgumentException, message mentions "neither" |
| Both parts and planning | 2: throws IllegalArgumentException, message mentions "not both" |

**Total: 8 scenarios, 26 assertions, all passing.**

## Anchor Points

- **ap.U5oDohccLN3tugPzK9TJa.E** -- `WorkflowParser` interface
- **ap.MyWV0mG6ZU8XaQOyo14l4.E** -- `WorkflowDefinition` domain model
- Both reference: `ref.ap.mmcagXtg6ulznKYYNKlNP.E` (design doc)

## Build Status

All tests pass. Full `./gradlew :app:build` is green.
