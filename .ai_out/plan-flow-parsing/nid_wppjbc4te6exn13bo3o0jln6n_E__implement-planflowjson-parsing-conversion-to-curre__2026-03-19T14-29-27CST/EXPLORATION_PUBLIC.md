# Exploration Summary: plan_flow.json Parsing + Conversion

## Dependencies — All CLOSED
- **nid_o5azwgdl76nnofttpt7ljgkua_E**: CurrentState init + persistence (CLOSED)
- **nid_kavqh23pdfq56cdli0fv4sm3u_E**: WorkflowParser (CLOSED)
- **nid_fjod8du6esers3ajur2h7tvgx_E**: AiOutputStructure.ensureStructure() (CLOSED)
- **nid_9kic96nh6mb8r5legcsvt46uy_E**: AiOutputStructure path resolution (CLOSED)

## Key Existing Components

### WorkflowParser (`core/workflow/WorkflowParser.kt`)
- Interface: `fun parse(workflowName: String, workingDirectory: Path): WorkflowDefinition`
- Uses `ShepherdObjectMapper.create()` (Jackson + KotlinModule, NON_NULL, FAIL_ON_UNKNOWN_PROPERTIES=false)
- Reads from `config/workflows/<name>.json`

### CurrentState (`core/state/CurrentState.kt`)
- `data class CurrentState(val parts: MutableList<Part>)`
- Has `appendExecutionParts(newParts: List<Part>)` method

### CurrentStateInitializer (`core/state/CurrentStateInitializer.kt`)
- `initializePart(part)` adds runtime fields: `status=NOT_STARTED`, `iteration.current=0`
- Companion object methods are reusable

### CurrentStatePersistence (`core/state/CurrentStatePersistence.kt`)
- `suspend fun flush(state: CurrentState)` — atomic write via temp+move
- Writes to `aiOutputStructure.currentStateJson()`

### AiOutputStructure (`core/filestructure/AiOutputStructure.kt`)
- `planFlowJson()` → `harness_private/plan_flow.json` path
- `ensureStructure(parts: List<Part>)` → creates .ai_out/ dirs for parts

### Data Classes (all in `core/state/`)
- Part: name, phase(Phase enum), description, subParts
- SubPart: name, role, agentType, model, status?, iteration?, sessionIds?
- Phase: PLANNING, EXECUTION (with @JsonProperty)
- IterationConfig: max, current(default=0)
- SubPartStatus: NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED

## plan_flow.json Schema (spec lines 270-295)
```json
{
  "parts": [{
    "name": "ui_design",
    "phase": "execution",
    "description": "...",
    "subParts": [
      { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet" },
      { "name": "review", "role": "UI_REVIEWER", "agentType": "ClaudeCode", "model": "sonnet",
        "iteration": { "max": 3 } }
    ]
  }]
}
```

## Conversion Logic (spec lines 428-452)
1. Read plan_flow.json from harness_private/
2. Deserialize as CurrentState (reuse ShepherdObjectMapper)
3. Validate: all parts phase=EXECUTION, required fields present
4. Initialize runtime fields via CurrentStateInitializer.initializePart()
5. Append execution parts to in-memory CurrentState
6. Call ensureStructure() for new execution parts
7. Flush CurrentState to disk
8. Delete plan_flow.json
9. Return execution parts

## Design Decision
- Reuse WorkflowParser/ShepherdObjectMapper (spec: "One parser handles everything")
- plan_flow.json uses same schema as workflow parts arrays
- Conversion lives as `PlanFlowConverter` class in `core/state` package
- On validation failure: throw PlanConversionException (AsgardBaseException)
