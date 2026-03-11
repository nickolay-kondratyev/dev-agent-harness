# Implementation Review: DirectLLM Tier Interfaces (Iteration 2)

**Signal: pass**

---

## Summary

All four previously flagged issues are resolved. The implementation correctly introduces three tier-scoped DirectLLM interfaces (`DirectQuickCheapLLM`, `DirectMediumLLM`, `DirectBudgetHighLLM`), GLM implementations that delegate to a shared `GlmAnthropicCompatibleApi`, a shared test helper `glmApiHttpContractTests`, and up-to-date KDoc in `SharedContextDescribeSpec`.

Tests pass (sanity_check.sh: exit 0).

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `getConfigurationObject()` hardcodes `GLM_HIGHEST_TIER` model name — misleading when called for the quick-cheap path

**File:** `app/src/main/kotlin/com/glassthought/ticketShepherd/core/Constants.kt` (lines 25–35)
**File:** `app/src/main/kotlin/com/glassthought/ticketShepherd/core/initializer/Initializer.kt` (lines 192–226)

`getConfigurationObject()` always sets `modelName = DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER` inside `GLMDirectLLMConfig`. Both `createGLMQuickCheapLLM` and `createGLMBudgetHighLLM` call this function to read `config.zAiGlmConfig.maxTokens`, but `createGLMQuickCheapLLM` then ignores `config.zAiGlmConfig.modelName` entirely and uses `Constants.DIRECT_LLM_API_MODEL_NAME.GLM_QUICK_CHEAP` directly. Calling a function and silently ignoring one of its result fields violates POLS — a reader would assume both fields are used.

The `modelName` field of `GLMDirectLLMConfig` is now only meaningful for the highest-tier path (and the integ test uses it via `config.zAiGlmConfig.modelName`). The config object does not represent a shared concept across both tiers.

Suggested fix: expose `maxTokens` directly from `Constants` and remove the misleading `modelName` from the shared config path:

```kotlin
// In Constants object:
fun resolveMaxTokens(): Int =
    System.getenv(Z_AI_API.MAX_TOKENS_ENV_VAR)?.toIntOrNull()
        ?: Z_AI_API.DEFAULT_MAX_TOKENS
```

Both factory methods call `Constants.resolveMaxTokens()` and reference their own model name constant directly. `getConfigurationObject()` / `GLMDirectLLMConfig` can either be removed or kept exclusively for the integ test (which can then read `GLM_HIGHEST_TIER` directly instead).

### 2. DRY violation — `apiToken` lookup duplicated in both factory methods

**File:** `app/src/main/kotlin/com/glassthought/ticketShepherd/core/initializer/Initializer.kt` (lines 195–198 and 213–216)

Both `createGLMQuickCheapLLM` and `createGLMBudgetHighLLM` contain the exact same env var read with identical error messages. Extract once:

```kotlin
private fun resolveApiToken(): String =
    System.getenv(Constants.Z_AI_API.API_TOKEN_ENV_VAR)
        ?: throw IllegalStateException(
            "Required environment variable [${Constants.Z_AI_API.API_TOKEN_ENV_VAR}] is not set"
        )
```

---

## Suggestions

### 1. Magic string `"2023-06-01"` should be a named constant

**File:** `app/src/main/kotlin/com/glassthought/ticketShepherd/core/supporting/directLLMApi/glm/GlmAnthropicCompatibleApi.kt` (line 53)

```kotlin
.addHeader("anthropic-version", "2023-06-01")
```

Per CLAUDE.md: "No magic numbers. Use named constants." A version string behaves like a magic number here. Belongs in `Constants.Z_AI_API`:

```kotlin
const val ANTHROPIC_API_VERSION = "2023-06-01"
```

### 2. `DirectMediumLLM` is declared but absent from `DirectLlmInfra` — intentional per spec

**File:** `app/src/main/kotlin/com/glassthought/ticketShepherd/core/supporting/directLLMApi/DirectLLM.kt` (line 29)

The KDoc says "Reserved — no V1 callers yet." This is correct per the CLAUDE.md spec. No action required; just confirming intentionality.

---

## Previously Flagged Issues — Verified Resolved

1. DRY: `GLMHighestTierApi` / `GLMQuickCheapApi` HTTP logic — resolved via `GlmAnthropicCompatibleApi` delegate pattern.
2. DRY: test suite duplication — resolved via `glmApiHttpContractTests` shared helper in `GlmApiHttpContractTestHelper.kt`.
3. One-assert-per-it in `max_tokens` test — resolved: two separate `it` blocks (`THEN request body has a max_tokens field` and `THEN request body max_tokens value matches configured value`).
4. Stale KDoc in `SharedContextDescribeSpec` — resolved: now references `shepherdContext.infra.directLlm.budgetHigh`.

---

## Documentation Updates Needed

None required for this iteration. The IMPORTANT issues above (items 1 and 2) are improvement items and do not block merge, but should be addressed as a follow-up or in this iteration if the engineer prefers consistency.
