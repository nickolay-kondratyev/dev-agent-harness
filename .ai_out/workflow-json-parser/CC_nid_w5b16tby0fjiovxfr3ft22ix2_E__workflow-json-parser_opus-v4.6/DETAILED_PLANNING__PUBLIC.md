# Detailed Implementation Plan: Workflow JSON Parser

## 1. Problem Understanding

**Goal**: Parse workflow JSON definition files into a Kotlin domain model so the Chainsaw harness can load and execute workflows.

**Constraints**:
- Jackson + Kotlin module for deserialization (design decision already made)
- Follow existing `TicketParser` pattern: interface + companion factory, `OutFactory` injection, `suspend fun`, `Dispatchers.IO`
- Fail-fast on file-not-found, malformed JSON, and missing required fields
- The `Part` schema is shared between static (straightforward) and planner-generated (plan.json) workflows
- No sealed class/enum for workflow types -- optional fields differentiate straightforward vs with-planning
- Package: `com.glassthought.chainsaw.core.workflow`

**Assumptions**:
- Jackson `2.17.x` is a reasonable version (latest stable as of writing)
- `kotlin-module` for Jackson is `jackson-module-kotlin`
- The parser does NOT resolve `executionPhasesFrom` at parse time (that is a separate concern for the workflow engine). It simply captures the path string.
- Validation is structural only (required fields present, types correct). Semantic validation (e.g., do referenced roles exist in the catalog?) is a separate concern.

---

## 2. High-Level Architecture

```
config/workflows/straightforward.json ──┐
config/workflows/with-planning.json ────┤
                                        ▼
                               WorkflowParser.parse(path)
                                        │
                                        ▼
                               WorkflowDefinition
                               ├── name: String
                               ├── parts: List<Part>?          (present for straightforward)
                               ├── planningPhases: List<Phase>? (present for with-planning)
                               ├── planningIteration: IterationConfig?
                               └── executionPhasesFrom: String?

Part                           Phase                    IterationConfig
├── name: String               └── role: String         └── max: Int
├── description: String
├── phases: List<Phase>
└── iteration: IterationConfig
```

**Key interface**: `WorkflowParser` (ap.U5oDohccLN3tugPzK9TJa.E)
**Key data model**: `WorkflowDefinition` (ap.MyWV0mG6ZU8XaQOyo14l4.E)

---

## 3. Implementation Phases

### Phase 1: Add Jackson Dependencies

**Goal**: Make Jackson + Kotlin module available for deserialization.

**Files to modify**:
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/build.gradle.kts`

**Steps**:
1. Add Jackson dependencies directly in `build.gradle.kts` (no version catalog entry -- consistent with how okhttp, snakeyaml, org.json are already declared):
   ```
   implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
   implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
   ```
2. Run `./gradlew :app:dependencies` to verify resolution.

**Verification**: Build succeeds with `./gradlew :app:build`.

---

### Phase 2: Create Domain Model Data Classes

**Goal**: Define the Kotlin data classes that represent a parsed workflow.

**File to create**: `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowDefinition.kt`

**Package**: `com.glassthought.chainsaw.core.workflow`

**Data classes** (all in one file, since they form a cohesive unit):

1. **`WorkflowDefinition`** (ap.MyWV0mG6ZU8XaQOyo14l4.E)
   - `name: String` -- workflow name (required)
   - `parts: List<Part>?` -- null for with-planning workflows
   - `planningPhases: List<Phase>?` -- null for straightforward workflows
   - `planningIteration: IterationConfig?` -- null for straightforward workflows
   - `executionPhasesFrom: String?` -- null for straightforward workflows

2. **`Part`**
   - `name: String` -- part name (required)
   - `description: String` -- what this part accomplishes (required)
   - `phases: List<Phase>` -- ordered list of phases (required, non-empty)
   - `iteration: IterationConfig` -- iteration config (required)

3. **`Phase`**
   - `role: String` -- role name matching a role catalog entry (required)

4. **`IterationConfig`**
   - `max: Int` -- maximum iteration count (required)

**Jackson annotations**: Use `@JsonProperty` only if JSON field names differ from Kotlin property names. Given the design doc JSON examples, the names align exactly -- **no annotations needed**. Jackson Kotlin module handles data classes natively.

**No `@JsonIgnoreProperties(ignoreUnknown = true)`**: Fail-fast on unexpected fields is safer. (If future flexibility is needed, this can be added later.)

WAIT -- actually, reconsider: the design doc says these are definition files that may evolve. Being strict about unknown properties is good for catching errors NOW. Keep the default (unknown properties cause errors). This can be revisited if needed.

**Verification**: File compiles.

---

### Phase 3: Create WorkflowParser Interface and Implementation

**Goal**: Define the parser contract and implementation.

**File to create**: `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParser.kt`

**Interface** (ap.U5oDohccLN3tugPzK9TJa.E):
```kotlin
interface WorkflowParser {
    suspend fun parse(path: Path): WorkflowDefinition

    companion object {
        fun standard(outFactory: OutFactory): WorkflowParser = WorkflowParserImpl(outFactory)
    }
}
```

**Implementation** (`WorkflowParserImpl`):
- Constructor: `WorkflowParserImpl(outFactory: OutFactory)`
- Private val: `out = outFactory.getOutForClass(WorkflowParserImpl::class)`
- Private val: `objectMapper` -- Jackson `ObjectMapper` configured with Kotlin module
  - `ObjectMapper().registerModule(KotlinModule.Builder().build())`
  - Configure `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES = true` to catch missing `max` in `IterationConfig`
- `parse(path: Path)`:
  1. Log debug with path
  2. Check file existence on IO dispatcher. If not found, throw `IllegalArgumentException("Workflow file not found: $path")`
  3. Read file content with `withContext(Dispatchers.IO) { path.readText() }`
  4. Deserialize with `objectMapper.readValue<WorkflowDefinition>(content)`. Jackson will throw `JsonProcessingException` on malformed JSON or missing fields.
  5. **Post-deserialization validation**: validate that the parsed definition is structurally coherent:
     - `name` must not be blank
     - Must have EITHER `parts` (straightforward) OR `planningPhases` + `planningIteration` + `executionPhasesFrom` (with-planning). Not both, not neither.
     - If `parts` is present, each part must have at least one phase.
     - If `planningPhases` is present, it must not be empty.
  6. Log info with workflow name
  7. Return the `WorkflowDefinition`

**Error handling**:
- File not found: `IllegalArgumentException`
- Malformed JSON: Let Jackson's `JsonProcessingException` propagate (it has good error messages)
- Structural validation failures: `IllegalArgumentException` with descriptive message

**Verification**: File compiles.

---

### Phase 4: Create Sample Workflow JSON Files

**Goal**: Provide real workflow definitions and test fixtures.

**Production files to create**:
1. `config/workflows/straightforward.json`
2. `config/workflows/with-planning.json`

**Content** (from the design doc, verbatim):

`straightforward.json`:
```json
{
  "name": "straightforward",
  "parts": [
    {
      "name": "main",
      "description": "Implement and review",
      "phases": [
        { "role": "IMPLEMENTOR_WITH_SELF_PLAN" },
        { "role": "IMPLEMENTATION_REVIEWER" }
      ],
      "iteration": { "max": 4 }
    }
  ]
}
```

`with-planning.json`:
```json
{
  "name": "with-planning",
  "planningPhases": [
    { "role": "PLANNER" },
    { "role": "PLAN_REVIEWER" }
  ],
  "planningIteration": { "max": 3 },
  "executionPhasesFrom": "plan.json"
}
```

**Test resource files to create** (under `app/src/test/resources/com/glassthought/chainsaw/core/workflow/`):
1. `straightforward.json` -- same as production file (validates the real format works)
2. `with-planning.json` -- same as production file
3. `multi-part.json` -- straightforward with multiple parts (tests list parsing)
4. `missing-name.json` -- missing the `name` field (tests fail-fast)
5. `malformed.json` -- invalid JSON syntax (tests fail-fast)
6. `empty-phases.json` -- a part with empty phases array (tests validation)
7. `neither-parts-nor-planning.json` -- has name but no parts and no planningPhases (tests validation)

**Verification**: Files are valid JSON (for the valid ones) and loadable from test classpath.

---

### Phase 5: Write Tests

**Goal**: Comprehensive BDD tests for the parser.

**File to create**: `app/src/test/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParserTest.kt`

**Test class**: `WorkflowParserTest : AsgardDescribeSpec`

**Helper function**:
```kotlin
fun resourcePath(name: String): Path =
    Path.of(
        WorkflowParserTest::class.java
            .getResource("/com/glassthought/chainsaw/core/workflow/$name")!!
            .toURI()
    )
```

**Test cases** (BDD structure):

```
GIVEN a straightforward workflow JSON
  WHEN parse is called
    THEN name is "straightforward"
    THEN parts list has 1 entry
    THEN first part name is "main"
    THEN first part description is "Implement and review"
    THEN first part has 2 phases
    THEN first phase role is "IMPLEMENTOR_WITH_SELF_PLAN"
    THEN second phase role is "IMPLEMENTATION_REVIEWER"
    THEN first part iteration max is 4
    THEN planningPhases is null
    THEN planningIteration is null
    THEN executionPhasesFrom is null

GIVEN a with-planning workflow JSON
  WHEN parse is called
    THEN name is "with-planning"
    THEN parts is null
    THEN planningPhases has 2 entries
    THEN first planning phase role is "PLANNER"
    THEN second planning phase role is "PLAN_REVIEWER"
    THEN planningIteration max is 3
    THEN executionPhasesFrom is "plan.json"

GIVEN a multi-part workflow JSON
  WHEN parse is called
    THEN parts list has 2 entries
    THEN second part name is correct
    THEN second part phases are correct

GIVEN a JSON file that does not exist
  WHEN parse is called
    THEN throws IllegalArgumentException
    THEN exception message contains the path

GIVEN a malformed JSON file
  WHEN parse is called
    THEN throws exception (JsonProcessingException or subclass)

GIVEN a JSON file missing the name field
  WHEN parse is called
    THEN throws exception

GIVEN a JSON file with empty phases array in a part
  WHEN parse is called
    THEN throws IllegalArgumentException

GIVEN a JSON file with neither parts nor planningPhases
  WHEN parse is called
    THEN throws IllegalArgumentException
```

**Verification**: All tests pass with `./gradlew :app:test`.

---

### Phase 6: Verify End-to-End Build

**Goal**: Ensure the full build is green.

**Steps**:
1. Run `./gradlew :app:build` and confirm clean build
2. Run `./gradlew :app:test` and confirm all tests pass (existing + new)

---

## 4. Technical Considerations

### ObjectMapper Configuration
- Register `KotlinModule` so Jackson can deserialize into Kotlin data classes with constructor parameters.
- `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES = true` so a missing `max` in `IterationConfig` fails at deserialization time rather than silently defaulting to 0.
- The `ObjectMapper` instance should be created once and stored as a private val in `WorkflowParserImpl` (it is thread-safe and expensive to create).

### Post-Deserialization Validation
Jackson handles syntactic validation (JSON structure, required fields via non-nullable Kotlin types). But we need additional semantic validation:
- `name` must not be blank
- Mutual exclusivity: a workflow must be either straightforward (has `parts`) or with-planning (has `planningPhases`), not both, not neither
- `parts` list must not contain parts with empty `phases`
- `planningPhases` list must not be empty

This validation should be a private method in `WorkflowParserImpl` called after deserialization. Throw `IllegalArgumentException` with descriptive messages.

### Nullable Fields for Polymorphism
The design doc explicitly says no sealed class/enum. Instead, `WorkflowDefinition` uses nullable fields:
- Straightforward: `parts` is non-null, `planningPhases`/`planningIteration`/`executionPhasesFrom` are null
- With-planning: `parts` is null, `planningPhases`/`planningIteration`/`executionPhasesFrom` are non-null

This is simple and sufficient. Downstream code will check which fields are present to determine the workflow type.

### Performance
Not a concern. Workflow files are small, parsed once at startup.

### Part Reuse for plan.json
The `Part` data class is intentionally the same schema used by planner-generated `plan.json`. The same `ObjectMapper` can parse a standalone `{ "parts": [...] }` document in the future. No special handling needed now -- just keep the data classes general.

---

## 5. Testing Strategy

### Key scenarios covered:
1. **Happy path**: Parse both workflow types with correct output
2. **Multi-part**: Verify list ordering is preserved
3. **File not found**: Fail-fast
4. **Malformed JSON**: Fail-fast (Jackson error)
5. **Missing required fields**: Fail-fast (Jackson + validation)
6. **Structural violations**: Empty phases, neither-parts-nor-planning

### What is NOT tested (separate concerns):
- Role name validation against the role catalog (future ticket)
- `executionPhasesFrom` path resolution (future ticket -- workflow engine)
- Integration with CLI argument parsing (separate ticket)

---

## 6. File Summary

### Files to Create

| Path | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowDefinition.kt` | Domain model: `WorkflowDefinition`, `Part`, `Phase`, `IterationConfig` (ap.MyWV0mG6ZU8XaQOyo14l4.E) |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParser.kt` | `WorkflowParser` interface + `WorkflowParserImpl` (ap.U5oDohccLN3tugPzK9TJa.E) |
| `config/workflows/straightforward.json` | Production straightforward workflow definition |
| `config/workflows/with-planning.json` | Production with-planning workflow definition |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/straightforward.json` | Test fixture |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/with-planning.json` | Test fixture |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/multi-part.json` | Test fixture: multiple parts |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/missing-name.json` | Test fixture: missing name field |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/malformed.json` | Test fixture: invalid JSON |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/empty-phases.json` | Test fixture: part with empty phases |
| `app/src/test/resources/com/glassthought/chainsaw/core/workflow/neither-parts-nor-planning.json` | Test fixture: invalid structure |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParserTest.kt` | BDD test class |

### Files to Modify

| Path | Change |
|------|--------|
| `app/build.gradle.kts` | Add `jackson-databind` and `jackson-module-kotlin` dependencies |

---

## 7. Anchor Points

- **ap.U5oDohccLN3tugPzK9TJa.E** -- `WorkflowParser` interface (placed in KDoc on the interface)
- **ap.MyWV0mG6ZU8XaQOyo14l4.E** -- `WorkflowDefinition` domain model (placed in KDoc on the data class)
- Both should reference: `ref.ap.mmcagXtg6ulznKYYNKlNP.E` (design doc in the ticket)

---

## 8. Open Questions / Decisions Needed

None. All design decisions have been made in the design doc and clarification phase. The implementation is well-constrained.

---

## 9. Multi-Part JSON Test Fixture Content

For the `multi-part.json` test fixture:
```json
{
  "name": "multi-part-workflow",
  "parts": [
    {
      "name": "design",
      "description": "Design the solution",
      "phases": [
        { "role": "DESIGNER" },
        { "role": "DESIGN_REVIEWER" }
      ],
      "iteration": { "max": 3 }
    },
    {
      "name": "implementation",
      "description": "Implement the solution",
      "phases": [
        { "role": "IMPLEMENTOR" },
        { "role": "IMPLEMENTATION_REVIEWER" }
      ],
      "iteration": { "max": 4 }
    }
  ]
}
```
