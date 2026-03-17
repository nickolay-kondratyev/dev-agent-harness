# Implementation Review

VERDICT: APPROVED (with one MINOR issue to address)

---

## Summary

Replaces two 42-line thin-wrapper classes (`GLMHighestTierApi`, `GLMQuickCheapApi`) with a pair of factory methods on a new `internal object GlmDirectLlmFactory`. Each factory method returns an anonymous object typed to the correct tier interface (`DirectBudgetHighLLM` or `DirectQuickCheapLLM`), delegating to the shared `GlmAnthropicCompatibleApi`. All test coverage is preserved. Tests pass (sanity_check.sh green).

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

None.

---

## MINOR Issues

### Residual parameter-list duplication inside `GlmDirectLlmFactory` itself

The ticket's stated goal was: "any new parameter added to `GlmAnthropicCompatibleApi` only needs updating in one place instead of three." The PR eliminates the third duplication site (the two wrapper classes) but still leaves the six-parameter list and the six-argument delegation call duplicated between `createBudgetHighLLM` and `createQuickCheapLLM`:

```
app/src/main/kotlin/com/glassthought/shepherd/core/supporting/directLLMApi/glm/GlmDirectLlmFactory.kt
```

Both methods are structurally identical — same six parameters in the same order, same anonymous-object body, same delegation call — differing only in the return type marker interface. Adding a seventh parameter to `GlmAnthropicCompatibleApi` now requires two edits in `GlmDirectLlmFactory` instead of three (the old two wrapper classes plus one in the original class), but the ticket claimed the goal was "one place." That claim is not fully met.

A straightforward fix is to extract a private helper that constructs `GlmAnthropicCompatibleApi` and type-wrap it at the call site:

```kotlin
internal object GlmDirectLlmFactory {

    fun createBudgetHighLLM(
        outFactory: OutFactory,
        httpClient: OkHttpClient,
        modelName: String,
        maxTokens: Int,
        apiEndpoint: String,
        apiToken: String,
    ): DirectBudgetHighLLM = object : DirectBudgetHighLLM {
        private val delegate = buildApi(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
        override suspend fun call(request: ChatRequest): ChatResponse = delegate.call(request)
    }

    fun createQuickCheapLLM(
        outFactory: OutFactory,
        httpClient: OkHttpClient,
        modelName: String,
        maxTokens: Int,
        apiEndpoint: String,
        apiToken: String,
    ): DirectQuickCheapLLM = object : DirectQuickCheapLLM {
        private val delegate = buildApi(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
        override suspend fun call(request: ChatRequest): ChatResponse = delegate.call(request)
    }

    private fun buildApi(
        outFactory: OutFactory,
        httpClient: OkHttpClient,
        modelName: String,
        maxTokens: Int,
        apiEndpoint: String,
        apiToken: String,
    ) = GlmAnthropicCompatibleApi(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
}
```

This is better than the current state, but still not "one place" (the public parameter lists in each method remain duplicated). The fully DRY solution would require a single entry point with a tier selector, but that adds complexity. For this codebase with only two tiers and a stable parameter set, the current state is acceptable — the remaining duplication is lower-risk than over-engineering. Flag this as a known residual and consider addressing if a third tier or new parameter arrives.

This is MINOR because the implementation is strictly better than before (three duplication sites reduced to two), correctness is not impacted, and the ticket itself is a simplification candidate — perfect 100% DRY is not required to approve.

---

## What Was Done Well

1. **Correctness is fully preserved.** The factory methods produce anonymous objects that implement `DirectBudgetHighLLM` and `DirectQuickCheapLLM` respectively, with identical delegation to `GlmAnthropicCompatibleApi`. The type-safety contracts are unchanged.

2. **No anchor points removed.** Neither deleted class contained any `@AnchorPoint` or `ap.*.E` identifiers. No stable references were broken.

3. **All test coverage preserved.** The 14 MockWebServer-backed HTTP contract tests still run for both tiers via `glmApiHttpContractTests`. The tier-specific interface-check `it` blocks remain in `GLMHighestTierApiTest` and `GLMQuickCheapApiTest`. The integration test `GLMHighestTierApiIntegTest` is updated and intact.

4. **Tests correctly updated, not worked around.** Factory calls in tests directly replace `GLMHighestTierApi(...)` / `GLMQuickCheapApi(...)` constructor calls with `GlmDirectLlmFactory.createBudgetHighLLM(...)` / `createQuickCheapLLM(...)`. No silent fallbacks, no skipped assertions.

5. **`GlmAnthropicCompatibleApi` doc comment updated.** The KDoc now correctly references `GlmDirectLlmFactory` as the creation point rather than the now-deleted classes.

6. **`internal` visibility on `GlmDirectLlmFactory` is appropriate.** The factory is not part of the public API; it is correctly scoped to the module. Tests in the same Gradle module can access it without issue.

7. **`ContextInitializerImpl` cleanly delegates to the factory.** The two private helper methods (`createGLMQuickCheapLLM`, `createGLMBudgetHighLLM`) each call the corresponding factory method — readable and focused.
