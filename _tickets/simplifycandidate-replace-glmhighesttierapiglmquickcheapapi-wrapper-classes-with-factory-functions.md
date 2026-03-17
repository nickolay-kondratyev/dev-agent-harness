---
closed_iso: 2026-03-17T22:08:18Z
id: nid_5mtpb9669k6kpwd7qzfhru6lx_E
title: "SIMPLIFY_CANDIDATE: Replace GLMHighestTierApi/GLMQuickCheapApi wrapper classes with factory functions"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:38:55Z
status_updated_iso: 2026-03-17T22:08:18Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, dry, llm, robustness]
---

GLMHighestTierApi and GLMQuickCheapApi (app/src/main/kotlin/com/glassthought/shepherd/core/supporting/directLLMApi/glm/) are each ~42-line classes where 38 lines are pure boilerplate delegation to GlmAnthropicCompatibleApi.

Both classes:
- Accept the same 7 constructor parameters
- Forward all parameters identically to GlmAnthropicCompatibleApi
- Add nothing except a type-safety wrapper (DirectBudgetHighLLM / DirectQuickCheapLLM)

**Simplification:** Replace both classes with factory functions (or companion object methods) directly in ContextInitializer where these objects are created. A single named factory function per tier keeps the type safety without the class-level boilerplate:

```kotlin
private fun createDirectBudgetHighLLM(...): DirectBudgetHighLLM =
    object : DirectBudgetHighLLM {
        private val delegate = GlmAnthropicCompatibleApi(...)
        override suspend fun call(request: ChatRequest) = delegate.call(request)
    }
```

This reduces ~80 lines to ~10, eliminates 2 files, and means any new parameter added to GlmAnthropicCompatibleApi only needs updating in one place instead of three.

**Robustness improvement:** Fewer places for parameter mismatch between the two tier classes and the underlying implementation.


## Notes

**2026-03-17T22:08:25Z**

Completed. Deleted GLMHighestTierApi.kt and GLMQuickCheapApi.kt. Created GlmDirectLlmFactory (internal object) with createBudgetHighLLM, createQuickCheapLLM, and private buildDelegate. GlmAnthropicCompatibleApi construction now in one place (was 3). All tests pass.
