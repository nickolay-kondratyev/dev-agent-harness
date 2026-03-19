# Use AiOutputStructure in ContextForAgentProviderImpl

## What was done

Moved PRIVATE.md path resolution from callers into `ContextForAgentProviderImpl`, using the already-injected `AiOutputStructure` dependency. This eliminates the need for callers to pass explicit `privateMdPath` in `AgentInstructionRequest` and removes the `@Suppress("UnusedPrivateProperty")` annotation.

## Changes

### AgentInstructionRequest (ContextForAgentProvider.kt)
- Replaced `abstract val privateMdPath: Path?` with `abstract val subPartName: String`
- All 5 subtypes updated: `DoerRequest`, `DoerFeedbackItemRequest`, `ReviewerRequest`, `PlannerRequest`, `PlanReviewerRequest`

### InstructionSection.PrivateMd (InstructionSection.kt)
- Changed from `data object` to `data class PrivateMd(val resolvedPath: Path?)`
- `render()` now reads from `resolvedPath` instead of `request.privateMdPath`

### ContextForAgentProviderImpl
- Removed `@Suppress("UnusedPrivateProperty")` from `aiOutputStructure`
- Added `resolvePrivateMdPath(request)` method that dispatches:
  - Execution requests (Doer, DoerFeedbackItem, Reviewer) -> `aiOutputStructure.executionPrivateMd(partName, subPartName)`
  - Planning requests (Planner, PlanReviewer) -> `aiOutputStructure.planningPrivateMd(subPartName)`
- Each `build*Plan()` method now passes `resolvePrivateMdPath(request)` to `InstructionSection.PrivateMd(...)`

### Callers (PartExecutorImpl.kt, InnerFeedbackLoop.kt)
- Replaced `privateMdPath = config.privateMdPath` with `subPartName = config.subPartName` in all `AgentInstructionRequest` construction sites
- `SubPartConfig.privateMdPath` kept as-is (still needed for compaction in PartExecutorImpl)

### Tests
- `ContextForAgentProviderAssemblyTest.kt`: Updated PRIVATE.md tests to create files at AiOutputStructure-resolved paths. Added test verifying execution vs planning path resolution.
- `ContextTestFixtures.kt`: Added `subPartName` to all fixture factory methods
- `InstructionSectionTest.kt`: Updated PrivateMd tests to construct `PrivateMd(resolvedPath)` directly
- `InstructionPlanAssemblerTest.kt`: Updated `PrivateMd` usage to use data class constructor

## Tests
- All tests pass (`./test.sh` exit 0)
