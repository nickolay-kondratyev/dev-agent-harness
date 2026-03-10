# Implementation Review: Workflow JSON Parser

## Overall Assessment: PASS

The implementation is solid, well-structured, and closely follows both the approved plan and existing codebase patterns. All 26 tests pass, the build is green, and sanity checks succeed. The code follows CLAUDE.md standards for Kotlin, testing, logging, and architecture.

---

## Issues Found

### MINOR Issues

#### 1. MINOR: Untested validation path -- blank name

**Where**: `WorkflowParser.kt` line 75-77 and `WorkflowParserTest.kt`

**What**: The `validate` method has `require(definition.name.isNotBlank())`, but there is no test fixture or test case exercising a workflow with a blank name (e.g., `"name": ""`). The existing `missing-name.json` fixture tests a *completely absent* name field, which Jackson catches before validation runs. The blank-name validation path has zero test coverage.

**Suggested fix**: Add a `blank-name.json` test fixture:
```json
{
  "name": "",
  "parts": [
    {
      "name": "main",
      "description": "test",
      "phases": [{ "role": "TEST" }],
      "iteration": { "max": 1 }
    }
  ]
}
```
And a test case:
```
GIVEN a JSON file with blank name
  WHEN parse is called
    THEN throws IllegalArgumentException
```

#### 2. MINOR: Untested validation path -- planningPhases present but planningIteration/executionPhasesFrom missing

**Where**: `WorkflowParser.kt` lines 98-109

**What**: The validation correctly checks that `planningIteration` and `executionPhasesFrom` must be non-null when `planningPhases` is present. However, there are no test fixtures exercising these two specific validation branches. Since these fields are nullable in the data class, Jackson will happily deserialize them as null, and only the post-deserialization validation catches the inconsistency. Without a test, a future refactor could accidentally remove these checks.

**Suggested fix**: Add test fixtures like `planning-missing-iteration.json` and `planning-missing-execution-from.json` with corresponding test cases.

---

## Positive Observations

### Plan Adherence
The implementation matches the approved plan precisely:
- Domain model data classes match the plan's schema exactly
- WorkflowParser interface + companion factory pattern matches TicketParser
- Jackson configuration includes `KotlinModule` and `FAIL_ON_NULL_FOR_PRIMITIVES`
- Post-deserialization validation covers all planned checks plus the "both parts AND planning" case (added from review feedback)
- All planned test fixtures are present, with the addition of `both-parts-and-planning.json`
- Anchor points are correctly placed

### Pattern Consistency with TicketParser
The implementation follows the TicketParser pattern faithfully:
- `interface WorkflowParser` with `companion object { fun standard(outFactory): WorkflowParser }`
- `WorkflowParserImpl(outFactory: OutFactory)` constructor injection
- `outFactory.getOutForClass(WorkflowParserImpl::class)` for logging
- `suspend fun parse(path: Path)` with `withContext(Dispatchers.IO)` for file reads
- Structured logging with `Val`/`ValType` using the same value types (`FILE_PATH_STRING`, `STRING_USER_AGNOSTIC`)
- `path.readText()` letting `NoSuchFileException` propagate naturally (no explicit `exists()` check -- avoids TOCTOU)

### Domain Model Quality
- Data classes are clean, minimal, and well-documented
- Nullable fields for polymorphic workflow types (straightforward vs with-planning) is a pragmatic approach that matches the design doc's explicit decision against sealed classes
- KDoc on each data class is clear and useful

### Testing Quality
- BDD structure with GIVEN/WHEN/THEN follows `AsgardDescribeSpec` conventions
- One assertion per `it` block throughout
- Happy paths thoroughly covered (11 assertions for straightforward, 7 for with-planning, 5 for multi-part)
- Error paths covered: file not found, malformed JSON, missing required fields, empty phases, neither-nor, both
- No silent fallbacks or masked assertions
- Test resource files are well-crafted

### Error Handling
- Fail-fast approach is consistent
- Jackson errors propagate with descriptive messages
- `IllegalArgumentException` for structural validation with messages that include the file path for debuggability
- Error messages for empty phases include the part name and index, which aids debugging

### Build Configuration
- Jackson dependencies added with clear comment, consistent with other dependency declarations
- Version `2.17.2` is reasonable and stable

### No Pre-existing Functionality Lost
- No existing test files were modified or removed
- Only additive changes to `build.gradle.kts`
- Ticket status change is the only non-additive change

---

## Final Verdict: PASS

The implementation is clean, well-tested, follows all codebase conventions, and matches the approved plan. The two MINOR issues (untested blank-name and untested planning-missing-fields validation paths) are low-risk gaps that could be addressed as a quick follow-up but do not block acceptance. The validation code itself is correct -- it just lacks test coverage for those specific branches.
