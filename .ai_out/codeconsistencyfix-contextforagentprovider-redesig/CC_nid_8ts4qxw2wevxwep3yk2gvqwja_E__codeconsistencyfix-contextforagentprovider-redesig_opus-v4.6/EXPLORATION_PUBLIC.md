# Exploration: ContextForAgentProvider Sealed Redesign

## Scope
- 7 files total: 2 production, 5 test (including fixtures)
- **No callers outside `core/context` package** — fully self-contained change
- Zero risk of breaking other modules

## Current State
- `ContextForAgentProvider.kt`: Interface with `assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path`
- `AgentRole` enum: `DOER, REVIEWER, PLANNER, PLAN_REVIEWER`
- `UnifiedInstructionRequest`: Flat data class with 20+ fields, many nullable
- `ContextForAgentProviderImpl.kt`: Dispatches on `AgentRole` enum, uses `requireNotNull` at runtime
- `RoleCatalogEntry`: Keep as-is (used by planner only)

## Spec Target (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E)
- Single method: `assembleInstructions(request: AgentInstructionRequest): Path`
- Sealed `AgentInstructionRequest` with 4 subtypes
- `ExecutionContext` composition for shared doer+reviewer fields
- `PrivateMd` section in all 4 role plans (silently skipped if absent)

## Files to Change
1. `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` — sealed hierarchy
2. `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` — dispatch on sealed type
3. `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt` — fixture factories
4. `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt` — update calls
5. `app/src/test/kotlin/com/glassthought/shepherd/core/context/ExecutionAgentInstructionsKeywordTest.kt` — update calls
6. `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlannerInstructionsKeywordTest.kt` — update calls
7. `app/src/test/kotlin/com/glassthought/shepherd/core/context/PlanReviewerInstructionsKeywordTest.kt` — update calls

## Key Design Decisions
- `ExecutionContext`: Composition over inheritance for shared doer+reviewer fields
- PrivateMd path: `${sub_part}/private/PRIVATE.md` — derived from outputDir
- All `requireNotNull` guards become compile-time via typed subtypes
- Remaining nullables: semantically meaningful (iteration 1, no-planning workflow)
