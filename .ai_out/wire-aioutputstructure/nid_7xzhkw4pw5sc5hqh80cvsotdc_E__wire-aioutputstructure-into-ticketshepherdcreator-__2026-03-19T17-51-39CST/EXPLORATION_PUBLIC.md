# Exploration Summary

## Current State

### AiOutputStructure — FULLY IMPLEMENTED
- Location: `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
- Constructor: `AiOutputStructure(repoRoot: Path, branch: String)`
- `ensureStructure(parts: List<Part>)` creates full directory skeleton
- All path resolution methods exist (branchRoot, harnessPrivateDir, executionPrivateMd, planningPrivateMd, etc.)

### TicketShepherdCreator — SPEC ONLY, NO CODE
- Spec: `doc/core/TicketShepherdCreator.md` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
- Class does NOT exist in source code yet
- Per spec: resolves workflow, parses ticket, creates branch, constructs dependencies, returns TicketShepherd

### TicketShepherd — SPEC ONLY, NO CODE
- Spec: `doc/core/TicketShepherd.md` (ref.ap.P3po8Obvcjw4IXsSUSU91.E)
- Class does NOT exist in source code yet
- Per spec: orchestrates ticket through workflow lifecycle

### ContextForAgentProviderImpl — EXISTS
- Location: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- Current constructor: `(outFactory: OutFactory, assembler: InstructionPlanAssembler)`
- Does NOT take AiOutputStructure currently
- Paths come from AgentInstructionRequest sealed subtypes, not computed internally

### Dependency Tickets — ALL CLOSED
- nid_fjod8du6esers3ajur2h7tvgx_E (ensureStructure) — CLOSED
- nid_9kic96nh6mb8r5legcsvt46uy_E (AiOutputStructure) — CLOSED
- nid_o4gj7swdejriooj5bex3b34vf_E (feedback alignment) — CLOSED

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/Part.kt` — Part data class
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/Phase.kt` — Phase enum (PLANNING, EXECUTION)
- `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt` — workflow parsing
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt` — shared infra context

## Critical Decision Point
TicketShepherdCreator and TicketShepherd don't exist as code. This ticket is about wiring AiOutputStructure, but the containers it wires INTO need to be created. This is skeleton creation + wiring, not full implementations of those classes.
