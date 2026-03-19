# Wire AiOutputStructure into TicketShepherdCreator — Implementation

## What Was Done

### 1. Added `allPartsForStructure()` to `WorkflowDefinition`
- New method returns the canonical parts list for `ensureStructure()` calls.
- Straightforward workflows return `parts!!`, with-planning workflows return `planningParts!!`.

### 2. Added `WorkflowDefinition` parameter to `TicketShepherdCreatorImpl`
- New constructor parameter `workflowDefinition: WorkflowDefinition`.
- `create()` now calls `aiOutputStructure.ensureStructure(workflowDefinition.allPartsForStructure())` before creating `CurrentState`.

### 3. Added `AiOutputStructure` to `ContextForAgentProviderImpl`
- New constructor parameter `private val aiOutputStructure: AiOutputStructure`.
- Updated `ContextForAgentProvider.standard()` factory to accept and pass `AiOutputStructure`.
- Suppressed `UnusedPrivateProperty` detekt warning since the dependency is wired for future use.

### 4. Added `AiOutputStructure` to `TicketShepherdDeps`
- New field `val aiOutputStructure: AiOutputStructure` in the `TicketShepherdDeps` data class.

### 5. Tests
- **WorkflowDefinitionTest**: Added 4 tests for `allPartsForStructure()` (straightforward and with-planning).
- **TicketShepherdCreatorTest**: Added `workflowDefinition` to all constructions. Added new test block verifying `ensureStructure()` creates real directories (harness_private, shared/plan, execution part feedback dirs, sub-part comm/private dirs).
- **ContextForAgentProviderAssemblyTest** + all keyword/ordering tests: Updated all `ContextForAgentProvider.standard(outFactory)` calls to include `ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE`.
- **TicketShepherdTest**: Updated all `TicketShepherdDeps` constructions to include `aiOutputStructure`.

## Files Modified

### Production
- `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt` — added `allPartsForStructure()`
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherdCreator.kt` — added `workflowDefinition` param, `ensureStructure()` call
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` — added `aiOutputStructure` param
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` — updated `standard()` factory
- `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt` — added `aiOutputStructure` to `TicketShepherdDeps`

### Tests
- `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinitionTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ExecutionAgentInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlannerInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlanReviewerInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionOrderingTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt`

## Tests
- All tests pass (`BUILD SUCCESSFUL`).
- New tests verify `allPartsForStructure()` for both workflow types.
- New tests verify real directory creation via `ensureStructure()` in the creator wiring.

## Notes
- `ContextForAgentProviderImpl.aiOutputStructure` has `@Suppress("UnusedPrivateProperty")` since it is wired for future use but not yet consumed in instruction assembly logic.
