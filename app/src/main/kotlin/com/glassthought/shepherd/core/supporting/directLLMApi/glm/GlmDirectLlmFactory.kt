package com.glassthought.shepherd.core.supporting.directLLMApi.glm

import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.supporting.directLLMApi.DirectBudgetHighLLM
import com.glassthought.shepherd.core.supporting.directLLMApi.DirectQuickCheapLLM
import okhttp3.OkHttpClient

/**
 * Factory for tier-specific GLM API instances backed by [GlmAnthropicCompatibleApi].
 *
 * [GlmAnthropicCompatibleApi] implements both tier interfaces; the model name passed
 * at construction captures the tier semantics. At the call site, explicit type
 * assignment narrows the instance to the required interface.
 *
 * Callers should reuse the returned instance rather than calling the factory repeatedly,
 * because OkHttp recommends a single shared client for connection pooling.
 */
internal object GlmDirectLlmFactory {

    fun createBudgetHighLLM(
        outFactory: OutFactory,
        httpClient: OkHttpClient,
        modelName: String,
        maxTokens: Int,
        apiEndpoint: String,
        apiToken: String,
    ): DirectBudgetHighLLM = create(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)

    fun createQuickCheapLLM(
        outFactory: OutFactory,
        httpClient: OkHttpClient,
        modelName: String,
        maxTokens: Int,
        apiEndpoint: String,
        apiToken: String,
    ): DirectQuickCheapLLM = create(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)

    fun create(
        outFactory: OutFactory,
        httpClient: OkHttpClient,
        modelName: String,
        maxTokens: Int,
        apiEndpoint: String,
        apiToken: String,
    ): GlmAnthropicCompatibleApi =
        GlmAnthropicCompatibleApi(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
}
