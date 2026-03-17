---
closed_iso: 2026-03-17T22:50:35Z
id: nid_1kt2gs66q4k2aoy16bywtaq9m_E
title: "SIMPLIFY_CANDIDATE: Merge ContextInitializer's two near-identical GLM factory methods into one parameterized method"
status: closed
deps: [nid_5mtpb9669k6kpwd7qzfhru6lx_E]
links: []
created_iso: 2026-03-17T21:39:41Z
status_updated_iso: 2026-03-17T22:50:35Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, dry, llm, initializer]
---

ContextInitializer (app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt) contains two factory methods that are structurally identical, differing only in:
- The return type (DirectQuickCheapLLM vs DirectBudgetHighLLM)
- The model name string passed to GlmAnthropicCompatibleApi

Both methods accept the same parameters (OutFactory, OkHttpClient, config vars) and construct a GlmAnthropicCompatibleApi with those parameters.

**Simplification:** After the GLM wrapper classes are replaced with factory functions (see related SIMPLIFY_CANDIDATE ticket), both creation paths collapse to a single parameterized helper:

```kotlin
private fun createGlmApi(model: String, outFactory: OutFactory, httpClient: OkHttpClient, ...): GlmAnthropicCompatibleApi
```

The two tier interfaces (DirectQuickCheapLLM, DirectBudgetHighLLM) are then satisfied by the same underlying object at the wiring site, with the type assigned explicitly.

**Robustness improvement:** Adding a new shared parameter (e.g., retry config, timeout) currently requires updating two factory methods in sync. With a single shared factory, it is updated once — no risk of divergence. Also eliminates the implicit contract that "these two methods must always remain in sync".

Note: This ticket is a natural follow-on to the GLM wrapper classes simplification ticket (nid_5mtpb9669k6kpwd7qzfhru6lx_E).


## Notes

**2026-03-17T22:50:31Z**

Completed.

Changes made:
1. GlmAnthropicCompatibleApi now implements DirectBudgetHighLLM and DirectQuickCheapLLM directly (tier is captured by model name at construction, not by type).
2. GlmDirectLlmFactory simplified: anonymous object wrappers removed; createBudgetHighLLM and createQuickCheapLLM now delegate to a new public create() method returning GlmAnthropicCompatibleApi directly.
3. ContextInitializer: replaced createGLMQuickCheapLLM and createGLMBudgetHighLLM with a single createGlmApi(model, outFactory, httpClient) parameterized helper. Common config params (maxTokens, apiEndpoint, apiToken) resolved once.

All 32 tests in GLMHighestTierApiTest and GLMQuickCheapApiTest pass.
