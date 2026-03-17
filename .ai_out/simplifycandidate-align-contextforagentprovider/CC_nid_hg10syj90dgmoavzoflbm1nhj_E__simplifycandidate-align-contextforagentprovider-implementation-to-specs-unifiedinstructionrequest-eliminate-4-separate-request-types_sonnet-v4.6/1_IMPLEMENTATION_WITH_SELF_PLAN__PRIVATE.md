# Implementation Plan — UnifiedInstructionRequest Refactor

## Task Understanding
Replace 4 separate request types + 4 interface methods with:
- Single `AgentRole` enum (DOER, REVIEWER, PLANNER, PLAN_REVIEWER)
- Single `UnifiedInstructionRequest` data class with all fields (role-specific ones nullable)
- Single `assembleInstructions(role, request)` method in interface and impl
- Impl dispatches on `AgentRole`

## Plan

**Goal**: Align ContextForAgentProvider implementation to spec — structural refactor only, no behavior change.

**Steps**:
1. [x] Read all files (done above)
2. [ ] Update `ContextForAgentProvider.kt`:
   - Remove 4 old request data classes
   - Add `AgentRole` enum
   - Add `UnifiedInstructionRequest` data class
   - Replace 4 interface methods with single `assembleInstructions(role, request)`
3. [ ] Update `ContextForAgentProviderImpl.kt`:
   - Replace 4 override methods with single `assembleInstructions(role, request)` that dispatches via `when(role)`
   - Adapt private builder methods to accept `UnifiedInstructionRequest` instead of role-specific requests
4. [ ] Update `ContextTestFixtures.kt`:
   - Replace 4 factory methods with ones returning `UnifiedInstructionRequest`
5. [ ] Update `ContextForAgentProviderAssemblyTest.kt` — use new API
6. [ ] Update `ExecutionAgentInstructionsKeywordTest.kt` — use new API
7. [ ] Update `PlannerInstructionsKeywordTest.kt` — use new API
8. [ ] Update `PlanReviewerInstructionsKeywordTest.kt` — use new API
9. [ ] Run `test.sh` and verify all tests pass

**Files touched**:
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ExecutionAgentInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlannerInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlanReviewerInstructionsKeywordTest.kt`

## Current State
- COMPLETE. All tests pass. BUILD SUCCESSFUL.
