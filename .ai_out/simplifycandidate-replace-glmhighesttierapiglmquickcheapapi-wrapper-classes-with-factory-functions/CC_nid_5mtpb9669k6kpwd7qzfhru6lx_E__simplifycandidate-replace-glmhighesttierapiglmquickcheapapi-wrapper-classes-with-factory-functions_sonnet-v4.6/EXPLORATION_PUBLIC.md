# GLM API Wrapper Classes — Exploration Report

## Summary

`GLMHighestTierApi` and `GLMQuickCheapApi` are 42-line thin wrapper classes with zero business logic.
All HTTP implementation lives in the shared `internal` class `GlmAnthropicCompatibleApi`.
These wrappers exist purely as type adapters for the marker interface hierarchy.

## Interface Hierarchy

```
DirectLLM (interface)
├── DirectQuickCheapLLM : DirectLLM  (fast/cheap tasks)
└── DirectBudgetHighLLM : DirectLLM  (expensive/quality tasks)
```

## Key Files

| File | Role |
|------|------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/directLLMApi/DirectLLM.kt` | Interface definitions |
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/directLLMApi/glm/GLMHighestTierApi.kt` | 42-line wrapper → implements `DirectBudgetHighLLM` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/directLLMApi/glm/GLMQuickCheapApi.kt` | 42-line wrapper → implements `DirectQuickCheapLLM` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/directLLMApi/glm/GlmAnthropicCompatibleApi.kt` | `internal` — all actual HTTP logic |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` | Instantiates both wrappers in `createGLMQuickCheapLLM` / `createGLMBudgetHighLLM` (lines 157-175) |

## Test Files

| File | Role |
|------|------|
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt` | Interface check + shared contract tests |
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMQuickCheapApiTest.kt` | Interface check + shared contract tests |
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GlmApiHttpContractTestHelper.kt` | Shared 14-test HTTP contract suite (MockWebServer-based) |
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiIntegTest.kt` | Integ test vs real Z.AI API (gated by `-PrunIntegTests=true`) |

## What Each Wrapper Class Does (identical structure)

```kotlin
class GLMHighestTierApi(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken) : DirectBudgetHighLLM {
    private val delegate = GlmAnthropicCompatibleApi(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
    override suspend fun call(request: ChatRequest): ChatResponse = delegate.call(request)
}
```
`GLMQuickCheapApi` is byte-for-byte identical except `DirectQuickCheapLLM`.

## Simplification Plan

Replace both classes with **`internal object GlmDirectLlmFactory`** in the `glm` package:
- `createBudgetHighLLM(...)`: DirectBudgetHighLLM
- `createQuickCheapLLM(...)`: DirectQuickCheapLLM

Each factory method returns an anonymous object that delegates to `GlmAnthropicCompatibleApi`.

`ContextInitializer` calls the factory methods. Tests also use the factory.
Wrapper class files (`GLMHighestTierApi.kt`, `GLMQuickCheapApi.kt`) are deleted.

## Key Constraints

- `GlmAnthropicCompatibleApi` is `internal` — accessible from same Gradle module (both main and test source sets)
- `internal` visibility for `GlmDirectLlmFactory` is appropriate (same module access for tests)
- `GlmApiHttpContractTestHelper` uses a factory lambda `createApi: (...) -> DirectLLM` — compatible with factory methods
- Tests in `com.glassthought.directLLMApi.glm` (test package) can access `internal` declarations from `com.glassthought.shepherd.core.supporting.directLLMApi.glm` (main package) because same Gradle module
