# Implementation: WorkflowDefinition + WorkflowParser

## What Was Done

Implemented `WorkflowDefinition` data class and `WorkflowParser` (interface + impl) for parsing
`config/workflows/*.json` files into validated, typed workflow definitions.

### WorkflowDefinition (ap.b4i1YCm3AvwEySAjDRoJg.E)

- Data class with two mutually exclusive modes: **straightforward** (`parts`) and **with-planning** (`planningParts` + `executionPhasesFrom`).
- `init` block enforces mutual exclusivity and requires `executionPhasesFrom` for with-planning workflows.
- Computed properties `isStraightforward` and `isWithPlanning` for easy mode checking.

### WorkflowParser (ap.kz1nvt0wyd2UpWsCGmm7Y.E)

- Interface with `suspend fun parse(workflowName, workingDirectory)` returning `WorkflowDefinition`.
- `WorkflowParserImpl` loads `config/workflows/<name>.json` via `ShepherdObjectMapper`, runs on IO dispatcher.
- Fail-fast validation: missing file, malformed JSON (catches `JacksonException`), phase constraints.
- Phase validation: straightforward parts must all be `Phase.EXECUTION`, planning parts must all be `Phase.PLANNING`.
- Follows `TicketParser` pattern: constructor injection of `OutFactory` + `DispatcherProvider`.

### Tests

- **WorkflowDefinitionTest** (9 tests): Validates data class construction, mutual exclusivity, and `executionPhasesFrom` requirement.
- **WorkflowParserTest** (23 tests): Parses actual `config/workflows/` files, verifies structure, tests fail-fast on missing files, malformed JSON, and wrong phase assignments.

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt` | Data class for workflow definitions |
| `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParser.kt` | Interface + impl for parsing workflow JSON |
| `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinitionTest.kt` | Unit tests for WorkflowDefinition |
| `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParserTest.kt` | Unit tests for WorkflowParser |

## Decisions

- Used `Path` parameter for `workingDirectory` instead of relying on `System.getProperty("user.dir")` — makes the parser testable and explicit.
- Caught `JacksonException` (not generic `Exception`) to satisfy detekt's `TooGenericExceptionCaught` rule.
- Reused existing `Part`, `SubPart`, `Phase` data classes from `com.glassthought.shepherd.core.state` — no duplication.
