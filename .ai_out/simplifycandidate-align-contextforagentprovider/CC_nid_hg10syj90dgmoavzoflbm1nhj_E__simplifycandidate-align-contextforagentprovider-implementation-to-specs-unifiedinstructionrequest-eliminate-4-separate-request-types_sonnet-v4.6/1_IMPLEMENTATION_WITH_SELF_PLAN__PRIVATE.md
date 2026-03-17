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
2. [x] Update `ContextForAgentProvider.kt`:
   - Remove 4 old request data classes
   - Add `AgentRole` enum
   - Add `UnifiedInstructionRequest` data class
   - Replace 4 interface methods with single `assembleInstructions(role, request)`
3. [x] Update `ContextForAgentProviderImpl.kt`:
   - Replace 4 override methods with single `assembleInstructions(role, request)` that dispatches via `when(role)`
   - Adapt private builder methods to accept `UnifiedInstructionRequest` instead of role-specific requests
4. [x] Update `ContextTestFixtures.kt`:
   - Replace 4 factory methods with ones returning `UnifiedInstructionRequest`
5. [x] Update `ContextForAgentProviderAssemblyTest.kt` — use new API
6. [x] Update `ExecutionAgentInstructionsKeywordTest.kt` — use new API
7. [x] Update `PlannerInstructionsKeywordTest.kt` — use new API
8. [x] Update `PlanReviewerInstructionsKeywordTest.kt` — use new API
9. [x] Run `test.sh` and verify all tests pass

**Iteration 2 — Layering fix + reviewer suggestions**:
10. [x] Move `RoleCatalogEntry` from `InstructionSections` to top-level in `ContextForAgentProvider.kt`
11. [x] Update `UnifiedInstructionRequest.roleCatalogEntries` to use `RoleCatalogEntry` (no qualifier)
12. [x] Remove `data class RoleCatalogEntry` from `InstructionSections` object
13. [x] Update `ContextTestFixtures.kt` to use `RoleCatalogEntry` (no qualifier)
14. [x] Add `partName` to debug log in `ContextForAgentProviderImpl.assembleInstructions`
15. [x] Add `requireNotNull` validation guard tests to `ContextForAgentProviderAssemblyTest.kt`
16. [x] Run `test.sh` — BUILD SUCCESSFUL

**Files touched**:
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSections.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ExecutionAgentInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlannerInstructionsKeywordTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlanReviewerInstructionsKeywordTest.kt`

## Current State
- COMPLETE. All tests pass. BUILD SUCCESSFUL. Ready for final review.
