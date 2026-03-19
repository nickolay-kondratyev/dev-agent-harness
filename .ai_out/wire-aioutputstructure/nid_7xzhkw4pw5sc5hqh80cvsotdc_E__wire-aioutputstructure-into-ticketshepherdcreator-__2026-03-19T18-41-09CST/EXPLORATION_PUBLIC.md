# Exploration Summary

## Current State

### AiOutputStructure
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
- Constructor: `(repoRoot: Path, branch: String)`
- `ensureStructure(parts: List<Part>)` creates full directory skeleton
- Fully implemented and tested (110+ path tests, separate ensureStructure tests)

### TicketShepherdCreatorImpl
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherdCreator.kt`
- Already receives `aiOutputStructure: AiOutputStructure` in constructor
- Already passes it to `CurrentStatePersistenceImpl`
- Does NOT yet call `ensureStructure()` — commented as future TODO
- Does NOT yet wire `ContextForAgentProvider` or `TicketShepherd`

### ContextForAgentProviderImpl
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- Constructor: `(outFactory: OutFactory, assembler: InstructionPlanAssembler)`
- Does NOT receive `AiOutputStructure` — not yet wired
- `privateMdPath` is already a field on `AgentInstructionRequest` (all subtypes) — callers provide it

### TicketShepherd (the class)
- NOT yet implemented in code. Only spec exists at `doc/core/TicketShepherd.md`
- Cannot wire what doesn't exist yet

### AgentInstructionRequest
- Sealed hierarchy with 4 subtypes (Doer, Reviewer, Planner, PlanReviewer)
- All have `privateMdPath: Path?` — the caller passes it, not ContextForAgentProviderImpl
- Path resolution happens upstream of ContextForAgentProvider

## Dependency Tickets — All CLOSED
- nid_9kic96nh6mb8r5legcsvt46uy_E — AiOutputStructure path resolution ✅
- nid_fjod8du6esers3ajur2h7tvgx_E — ensureStructure() implementation ✅
- nid_o4gj7swdejriooj5bex3b34vf_E — Feedback alignment to 3-dir structure ✅

## Key Findings
1. `TicketShepherdCreatorImpl` already has `AiOutputStructure` injected — added in earlier work
2. `ensureStructure()` is NOT yet called in `create()` — this is the main work
3. `ContextForAgentProvider` does NOT need `AiOutputStructure` directly — `privateMdPath` comes from callers via `AgentInstructionRequest`
4. `TicketShepherd` class doesn't exist yet — cannot wire into it
5. The factory method `ContextForAgentProvider.standard()` exists but doesn't use AiOutputStructure

## Scope for This Ticket
Per ticket notes: focus on (1) `ensureStructure()` call with correct parts, (2) injection verification tests.
- ContextForAgentProviderImpl does NOT need AiOutputStructure as constructor param (paths come via request)
- TicketShepherd doesn't exist — can't wire yet
- Main work: call `ensureStructure()` in `create()` with ALL workflow parts
