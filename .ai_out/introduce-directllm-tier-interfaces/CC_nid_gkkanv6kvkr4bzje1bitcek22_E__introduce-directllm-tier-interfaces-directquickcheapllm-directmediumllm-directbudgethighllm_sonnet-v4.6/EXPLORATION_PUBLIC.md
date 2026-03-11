# Exploration: DirectLLM Tier Interfaces

## Current State

### DirectLLM.kt
- Base `DirectLLM` interface with `ChatRequest`/`ChatResponse` data classes
- **No tier interfaces yet** — single monolithic interface

### GLMHighestTierApi.kt
- Implements `DirectLLM` (base interface)
- Uses Z.AI Anthropic-compatible endpoint: `https://api.z.ai/api/anthropic/v1/messages`
- Model: `glm-5` (from `Constants.DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER`)
- Parameters: modelName, maxTokens, apiEndpoint, apiToken are all constructor-injected

### Constants.kt
- `DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER = "glm-5"`
- `Z_AI_API.CHAT_COMPLETIONS_ENDPOINT = "https://api.z.ai/api/anthropic/v1/messages"`
- `Z_AI_API.API_TOKEN_ENV_VAR = "Z_AI_GLM_API_TOKEN"`
- `Z_AI_API.DEFAULT_MAX_TOKENS = 4096`
- Config driven via `GLMDirectLLMConfig(modelName, maxTokens)` embedded in `Config`

### Initializer.kt - DirectLlmInfra
```kotlin
data class DirectLlmInfra(
    val glmDirectLLM: DirectLLM,       // ← needs replacement with tier-scoped fields
    internal val httpClient: OkHttpClient,
)
```

### initializeImpl
- Creates single `GLMHighestTierApi` via `createGLMDirectLLM(outFactory, httpClient)`
- Wraps it in `DirectLlmInfra(glmDirectLLM = glmDirectLLM, httpClient = httpClient)`

### BranchNameBuilder.kt
- Currently a pure slugifier (no LLM). Will use `DirectQuickCheapLLM` in future.
- Currently stateless, no LLM dependency.

### CallGLMApiSandboxMain.kt
- Uses `infra.directLlm.glmDirectLLM` — needs update after field rename

### Tests
- `GLMHighestTierApiTest.kt` — 14 unit tests (mock HTTP)
- `GLMHighestTierApiIntegTest.kt` — real API integration test

## Required Changes

### 1. Add tier interfaces to `DirectLLM.kt`
```kotlin
interface DirectQuickCheapLLM : DirectLLM   // fast, low-cost
interface DirectMediumLLM : DirectLLM        // mid-tier (no V1 callers)
interface DirectBudgetHighLLM : DirectLLM    // expensive (FailedToConvergeUseCase)
```

### 2. Add constant to `Constants.kt`
```kotlin
const val GLM_QUICK_CHEAP = "glm-4.7-flash"   // V1 DirectQuickCheapLLM model
```

### 3. Create `GLMQuickCheapApi` implementing `DirectQuickCheapLLM`
- Mirror `GLMHighestTierApi` structure
- Uses `glm-4.7-flash` model

### 4. Update `GLMHighestTierApi`
- Change `implements DirectLLM` → `implements DirectBudgetHighLLM`

### 5. Refactor `DirectLlmInfra`
```kotlin
data class DirectLlmInfra(
    val quickCheap: DirectQuickCheapLLM,
    val budgetHigh: DirectBudgetHighLLM,
    internal val httpClient: OkHttpClient,
)
```

### 6. Update `Initializer.initializeImpl`
- Create two instances: GLMQuickCheapApi + GLMHighestTierApi (renamed to `createGLMBudgetHighLLM`)
- Wire both into `DirectLlmInfra`

### 7. Update `CallGLMApiSandboxMain.kt`
- Use `infra.directLlm.budgetHigh` instead of `infra.directLlm.glmDirectLLM`

### 8. Update tests to cover tier interface implementation
