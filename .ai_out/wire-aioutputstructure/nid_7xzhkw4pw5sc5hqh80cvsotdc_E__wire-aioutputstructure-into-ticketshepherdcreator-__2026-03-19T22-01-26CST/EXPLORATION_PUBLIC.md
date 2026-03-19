# Exploration: Wire AiOutputStructure into TicketShepherdCreator

## Current State

### AiOutputStructure (fully implemented)
- `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
- Constructor: `(repoRoot: Path, branch: String)`
- `ensureStructure(parts: List<Part>)` creates full directory skeleton
- All path resolution methods implemented and tested

### TicketShepherdCreatorImpl (partially implemented)
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherdCreator.kt`
- Already has `AiOutputStructure` as constructor dependency ✓
- Already uses it for `CurrentStatePersistenceImpl` ✓
- **Missing**: `ensureStructure()` call (needs `WorkflowDefinition` to get parts)
- **Missing**: Full `TicketShepherd` construction (future TODO)

### ContextForAgentProviderImpl
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- Constructor: `(outFactory: OutFactory, assembler: InstructionPlanAssembler)`
- Does NOT have `AiOutputStructure` - paths come pre-resolved in `AgentInstructionRequest`
- Ticket asks to inject `AiOutputStructure` here

### TicketShepherd
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt`
- Constructor: `(deps: TicketShepherdDeps, currentState, originatingBranch, tryNumber)`
- `TicketShepherdDeps` does NOT include `AiOutputStructure`
- Ticket asks to add it

### WorkflowDefinition
- `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt`
- Straightforward: `parts` (List<Part>, all EXECUTION)
- With-planning: `planningParts` (List<Part>, all PLANNING) + `executionPhasesFrom`

## All 3 Dependency Tickets: CLOSED ✓
- nid_fjod8du6esers3ajur2h7tvgx_E (ensureStructure tests) - closed
- nid_9kic96nh6mb8r5legcsvt46uy_E (path resolution) - closed
- nid_o4gj7swdejriooj5bex3b34vf_E (feedback alignment) - closed

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherdCreator.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`
