# Exploration: Use AiOutputStructure in ContextForAgentProviderImpl

## Current State

### ContextForAgentProviderImpl
- File: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- Has `aiOutputStructure: AiOutputStructure` injected but marked `@Suppress("UnusedPrivateProperty")`
- Dispatches on sealed `AgentInstructionRequest` types to build instruction plans

### AgentInstructionRequest (sealed hierarchy)
- File: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`
- Abstract field: `privateMdPath: Path?` — currently passed explicitly from callers
- 5 subtypes: DoerRequest, DoerFeedbackItemRequest, ReviewerRequest, PlannerRequest, PlanReviewerRequest
- Execution types (Doer*, Reviewer) have `executionContext: ExecutionContext` with `partName`
- Planning types (Planner, PlanReviewer) have no execution context

### InstructionSection.PrivateMd
- File: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` (lines 59-66)
- `data object` that reads `request.privateMdPath` in render()
- Checks file existence and non-blank content

### AiOutputStructure
- File: `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
- `executionPrivateMd(partName, subPartName)` → `.ai_out/${branch}/execution/${part}/${subPart}/private/PRIVATE.md`
- `planningPrivateMd(subPartName)` → `.ai_out/${branch}/planning/${subPart}/private/PRIVATE.md`

### SubPartConfig
- File: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfig.kt`
- Has both `partName`, `subPartName`, AND `privateMdPath: Path?`
- `privateMdPath` is also used by PartExecutorImpl for compaction (line 426) — must remain in SubPartConfig

### Callers creating AgentInstructionRequest
1. `PartExecutorImpl.sendDoerInstructions()` — passes `doerConfig.privateMdPath`
2. `PartExecutorImpl.sendReviewerInstructions()` — passes `revConfig.privateMdPath`
3. `InnerFeedbackLoop` — passes `doerConfig.privateMdPath` for DoerFeedbackItemRequest

### Tests
- `ContextForAgentProviderAssemblyTest.kt` — comprehensive tests including PRIVATE.md scenarios
- `ContextTestFixtures.kt` — has `TEST_AI_OUTPUT_STRUCTURE` and factory methods for all request types

## Design Decision

### Approach: Add `subPartName` to request, resolve path in provider

1. **Add `subPartName: String` as abstract field** on `AgentInstructionRequest`
2. **Remove `privateMdPath: Path?`** from `AgentInstructionRequest`
3. **Change `InstructionSection.PrivateMd`** from `data object` to `data class` with resolved `privateMdPath: Path?`
4. **In `ContextForAgentProviderImpl`**: resolve path before building plan:
   - Execution requests: `aiOutputStructure.executionPrivateMd(executionContext.partName, subPartName)`
   - Planning requests: `aiOutputStructure.planningPrivateMd(subPartName)`
5. **Remove `@Suppress("UnusedPrivateProperty")`**
6. **Update callers**: pass `subPartName` instead of `privateMdPath` in AgentInstructionRequest construction
7. **Keep `SubPartConfig.privateMdPath`**: still needed for compaction in PartExecutorImpl

### Files to modify
- `ContextForAgentProvider.kt` (AgentInstructionRequest sealed class)
- `ContextForAgentProviderImpl.kt` (path resolution + remove suppress)
- `InstructionSection.kt` (PrivateMd → data class)
- `PartExecutorImpl.kt` (update request construction)
- `InnerFeedbackLoop.kt` (update request construction)
- `SubPartConfig.kt` (no change needed — keeps its own privateMdPath for compaction)
- `ContextForAgentProviderAssemblyTest.kt` (update tests)
- `ContextTestFixtures.kt` (update fixtures)
- `PartExecutorImplTest.kt` (update test fixtures)
- `InnerFeedbackLoopTest.kt` (update test fixtures)
