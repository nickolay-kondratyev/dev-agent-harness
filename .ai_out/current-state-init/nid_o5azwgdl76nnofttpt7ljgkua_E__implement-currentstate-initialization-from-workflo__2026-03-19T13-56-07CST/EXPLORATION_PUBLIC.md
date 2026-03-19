# Exploration Summary: CurrentState Initialization

## Existing Types (all implemented, dependency tickets closed)

### Package: `com.glassthought.shepherd.core.state`
- **CurrentState** (`CurrentState.kt`): `data class(val parts: MutableList<Part>)`
- **Part** (`Part.kt`): `data class(name, phase: Phase, description, subParts: List<SubPart>)`
- **SubPart** (`SubPart.kt`): `data class(name, role, agentType, model, status?, iteration?, sessionIds?)`
- **SubPartStatus** (`SubPartStatus.kt`): enum `NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED`
- **IterationConfig** (`IterationConfig.kt`): `data class(max: Int, current: Int = 0)`
- **SessionRecord** (`SessionRecord.kt`): `data class(handshakeGuid, agentSession: AgentSessionInfo, agentType, model, timestamp)`
- **Phase** (`Phase.kt`): enum `PLANNING, EXECUTION` with `@JsonProperty` lowercase
- **SubPartStateTransition** (`SubPartStateTransition.kt`): sealed class with Spawn, Complete, Fail, IterateContinue + extension fns
- **ShepherdObjectMapper** (`ShepherdObjectMapper.kt`): NON_NULL inclusion, KotlinModule, FAIL_ON_UNKNOWN=false

### Package: `com.glassthought.shepherd.core.workflow`
- **WorkflowDefinition** (`WorkflowDefinition.kt`): `data class(name, parts?, planningParts?, executionPhasesFrom?)`
  - Mutually exclusive: `parts` XOR `planningParts`
  - `isWithPlanning` / `isStraightforward` boolean properties

### Package: `com.glassthought.shepherd.core.filestructure`
- **AiOutputStructure** (`AiOutputStructure.kt`): path resolution, key method `currentStateJson(): Path`

## Spec Requirements (doc/schema/plan-and-current-state.md)

### Initialization (lines 428-452)
- **Straightforward**: Parts from `WorkflowDefinition.parts` → all `phase: EXECUTION`, add `status: NOT_STARTED`, `iteration.current: 0` on reviewers
- **With-planning**: ONE entry from `planningParts` → `phase: PLANNING`, add `status: NOT_STARTED`, `iteration.current: 0` on reviewers

### Runtime Fields Added
- `status: NOT_STARTED` on every sub-part
- `iteration.current: 0` on every reviewer sub-part (where `iteration` block exists in workflow definition)
- `sessionIds` absent initially

### Persistence (lines 597-622)
- Full file rewrite, atomic (temp file + rename)
- Flush after EVERY mutation
- Write to `harness_private/current_state.json` via `AiOutputStructure.currentStateJson()`

## Existing Tests
- `PlanCurrentStateModelTest.kt` — Jackson round-trip tests for data classes
- `SubPartStateTransitionTest.kt` — state transition validation tests
- Follow `AsgardDescribeSpec`, BDD GIVEN/WHEN/THEN pattern

## Key Invariant
"No component reads current_state.json from disk during a run." Disk file is for durability/observability only.
