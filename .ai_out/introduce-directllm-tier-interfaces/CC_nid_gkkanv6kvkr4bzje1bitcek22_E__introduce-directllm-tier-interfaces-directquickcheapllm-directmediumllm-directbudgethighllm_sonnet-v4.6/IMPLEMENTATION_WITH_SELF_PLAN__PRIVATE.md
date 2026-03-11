# Implementation Plan: DirectLLM Tier Interfaces

## Goal
Introduce `DirectQuickCheapLLM`, `DirectMediumLLM`, and `DirectBudgetHighLLM` tier interfaces extending `DirectLLM`. Wire `GLMHighestTierApi` to `DirectBudgetHighLLM`, create `GLMQuickCheapApi` for `DirectQuickCheapLLM`, and update all callsites.

## Status: COMPLETE (Iteration 3) — BUILD SUCCESSFUL, 150 tests, 0 failures

## Steps (Iteration 1 — all done)
- [x] 1. Read exploration file and all relevant source files
- [x] 2. Add tier interfaces to `DirectLLM.kt`
- [x] 3. Add `GLM_QUICK_CHEAP` constant to `Constants.kt`
- [x] 4. Create `GLMQuickCheapApi.kt` implementing `DirectQuickCheapLLM`
- [x] 5. Update `GLMHighestTierApi.kt` to implement `DirectBudgetHighLLM`
- [x] 6. Refactor `DirectLlmInfra` in `Initializer.kt` (rename fields, add quickCheap/budgetHigh)
- [x] 7. Update `initializeImpl` wiring (two factory methods)
- [x] 8. Update `CallGLMApiSandboxMain.kt` (use `budgetHigh`)
- [x] 9. Add type-check test for `GLMHighestTierApi implements DirectBudgetHighLLM`
- [x] 10. Create `GLMQuickCheapApiTest.kt`
- [x] 11. Run tests and verify all pass

## Steps (Iteration 2 — all done)
- [x] 1. Create `GlmAnthropicCompatibleApi.kt` (internal delegate class)
- [x] 2. Refactor `GLMHighestTierApi.kt` to delegate to it
- [x] 3. Refactor `GLMQuickCheapApi.kt` to delegate to it
- [x] 4. Create `GlmApiHttpContractTestHelper.kt` with shared test extension function
- [x] 5. Refactor `GLMHighestTierApiTest.kt` — use helper, type-check only
- [x] 6. Refactor `GLMQuickCheapApiTest.kt` — use helper, type-check only
- [x] 7. Fix stale KDoc in `SharedContextDescribeSpec.kt`
- [x] 8. Run tests and confirm BUILD SUCCESSFUL

## Steps (Iteration 3 — all done)
- [x] 1. Fix 1: Add `Constants.resolveMaxTokens()`, remove `getConfigurationObject()`/`Config`/`GLMDirectLLMConfig`
- [x] 2. Fix 2: Extract `resolveApiToken()` in `InitializerImpl`
- [x] 3. Fix 3: Add `Constants.Z_AI_API.ANTHROPIC_API_VERSION`, use in `GlmAnthropicCompatibleApi`
- [x] 4. Update `GLMHighestTierApiIntegTest` (was using removed `getConfigurationObject()`)
- [x] 5. Run tests — BUILD SUCCESSFUL, 150 tests, 0 failures
