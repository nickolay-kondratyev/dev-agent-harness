# Exploration: WorkflowDefinition + WorkflowParser

## Key Findings

### Data Classes Already Exist
All core data classes (Part, SubPart, Phase, IterationConfig, CurrentState, SessionRecord) are implemented in `app/src/main/kotlin/com/glassthought/shepherd/core/state/`.

### Jackson Config Ready
`ShepherdObjectMapper.kt` exists with KotlinModule, NON_NULL inclusion, FAIL_ON_UNKNOWN_PROPERTIES=false.

### Workflow JSON Files
- `config/workflows/straightforward.json` — has `parts` array with execution phase
- `config/workflows/with-planning.json` — has `planningParts` array + `executionPhasesFrom`

### Parser Patterns to Follow
- `TicketParser.kt` — interface + impl, OutFactory logging, DispatcherProvider for async IO, fail-fast with require()
- `RoleCatalogLoader.kt` — same pattern

### Package Location
Target: `com.glassthought.shepherd.core.workflow` (per ticket) or `com.glassthought.shepherd.core.state` (where data classes live)

### Spec Reference
`doc/schema/plan-and-current-state.md` lines 454-514 — ref.ap.56azZbk7lAMll0D4Ot2G0.E

### Key Constraints
- Straightforward: `parts` non-null, all phase="execution"
- With-planning: `planningParts` non-null + `executionPhasesFrom` required, phase="planning"
- Mutually exclusive: exactly one of parts/planningParts
