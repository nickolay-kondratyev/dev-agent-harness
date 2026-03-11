# Implementation: DirectLLM Tier Interfaces (Iteration 3 — Final Clean-Up)

## Status: COMPLETE — all issues resolved, BUILD SUCCESSFUL, 150 tests (0 failures)

---

## Iteration 1 Summary

Introduced three tier-scoped sub-interfaces of `DirectLLM` (`DirectQuickCheapLLM`, `DirectMediumLLM`, `DirectBudgetHighLLM`) and wired them through the initializer and implementations.

- `DirectLLM.kt` — Added three tier interfaces
- `Constants.kt` — Added `GLM_QUICK_CHEAP` constant
- `GLMQuickCheapApi.kt` — New class implementing `DirectQuickCheapLLM`
- `GLMHighestTierApi.kt` — Now implements `DirectBudgetHighLLM`
- `Initializer.kt` — `DirectLlmInfra` fields `quickCheap` + `budgetHigh`, two factory methods
- `CallGLMApiSandboxMain.kt` — Updated field reference to `budgetHigh`
- Tests added: `GLMHighestTierApiTest` type-check, `GLMQuickCheapApiTest` (15 tests)

---

## Iteration 2: Review Issues Fixed

### Issue 1: DRY violation — production code

Created `GlmAnthropicCompatibleApi.kt` as an `internal` class in the `glm` package encapsulating all HTTP logic (request construction, response parsing, error handling, logging).

Both `GLMHighestTierApi` and `GLMQuickCheapApi` were refactored to:
- Instantiate a `GlmAnthropicCompatibleApi` delegate
- Forward `call()` to the delegate

The concrete classes are now thin wrappers that implement only their tier interface.

**Files:**
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/supporting/directLLMApi/glm/GlmAnthropicCompatibleApi.kt` (new)
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/supporting/directLLMApi/glm/GLMHighestTierApi.kt` (refactored to delegate)
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/supporting/directLLMApi/glm/GLMQuickCheapApi.kt` (refactored to delegate)

### Issue 2: DRY violation — tests

Created `GlmApiHttpContractTestHelper.kt` with an extension function `fun AsgardDescribeSpec.glmApiHttpContractTests(modelName, createApi)` containing all 15 shared HTTP contract test cases.

Both `GLMHighestTierApiTest` and `GLMQuickCheapApiTest` now:
- Call `glmApiHttpContractTests(modelName) { ... }` with a factory lambda
- Retain only the tier-specific type-check `it` block

**Files:**
- `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GlmApiHttpContractTestHelper.kt` (new)
- `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt` (refactored)
- `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMQuickCheapApiTest.kt` (refactored)

### Issue 3: One-assert-per-it violation

Replaced `it("THEN request body contains max_tokens parameter")` (2 assertions) with:
- `it("THEN request body has a max_tokens field")` — `body.has("max_tokens") shouldBe true`
- `it("THEN request body max_tokens value matches configured value")` — `body.getInt("max_tokens") shouldBe maxTokens`

Lives in `GlmApiHttpContractTestHelper.kt`, so it applies to both test classes.

### Issue 4: Stale KDoc in SharedContextDescribeSpec

Updated `SharedContextDescribeSpec.kt` line 32:
- Before: `shepherdContext.infra.directLlm.glmDirectLLM`
- After: `shepherdContext.infra.directLlm.budgetHigh`

---

## Iteration 3: Clean-Up Issues Fixed

### Fix 1 — POLS violation: removed misleading `getConfigurationObject()` usage

`createGLMQuickCheapLLM()` was calling `Constants.getConfigurationObject()` which returned a struct whose `modelName` was `GLM_HIGHEST_TIER` — the wrong model. Only `maxTokens` was being used, but the struct silently carried the wrong model name.

**Resolution:** Added `Constants.resolveMaxTokens(): Int` — returns the resolved max tokens directly without building any model-tied struct. Removed `getConfigurationObject()`, `Config`, and `GLMDirectLLMConfig` entirely. Both factory methods now call `Constants.resolveMaxTokens()`.

Updated `GLMHighestTierApiIntegTest` to use `Constants.DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER` and `Constants.resolveMaxTokens()` directly (was using `config.zAiGlmConfig.modelName/maxTokens`).

### Fix 2 — DRY violation: extracted `resolveApiToken()` in `InitializerImpl`

Both factory methods had identical env var read + exception throw for the API token.

**Resolution:** Extracted `private fun resolveApiToken(): String` in `InitializerImpl`. Both factory methods now call `resolveApiToken()`.

### Fix 3 — Magic string `"2023-06-01"` in `GlmAnthropicCompatibleApi`

**Resolution:** Added `Constants.Z_AI_API.ANTHROPIC_API_VERSION = "2023-06-01"` with doc comment. `GlmAnthropicCompatibleApi` now uses `Constants.Z_AI_API.ANTHROPIC_API_VERSION`.

---

## Test Results

- 150 total tests, 0 failures
- `GLMHighestTierApiTest`: 16 tests (15 HTTP contract + 1 type-check)
- `GLMQuickCheapApiTest`: 16 tests (15 HTTP contract + 1 type-check)
- All other tests unchanged and passing

---

## Key Decisions

- Used extension function on `AsgardDescribeSpec` for the shared test helper. This is idiomatic Kotest and gives natural access to `outFactory` inside the helper without extra plumbing.
- `GlmAnthropicCompatibleApi` is `internal` — it is an implementation detail of the `glm` package, not part of the public API.
- The `Out` logging in `GlmAnthropicCompatibleApi` uses `GlmAnthropicCompatibleApi::class` as the log class name, accurately reflecting where the HTTP work happens.
- `resolveMaxTokens()` lives on `Constants` object (not `Z_AI_API` nested object) because it reads from `Z_AI_API` constants and returns a resolved value — placing it at the `Constants` level keeps it accessible without over-nesting.
