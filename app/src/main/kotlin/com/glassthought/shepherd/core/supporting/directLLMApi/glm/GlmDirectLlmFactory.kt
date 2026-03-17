package com.glassthought.shepherd.core.supporting.directLLMApi.glm

import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.supporting.directLLMApi.ChatRequest
import com.glassthought.shepherd.core.supporting.directLLMApi.ChatResponse
import com.glassthought.shepherd.core.supporting.directLLMApi.DirectBudgetHighLLM
import com.glassthought.shepherd.core.supporting.directLLMApi.DirectQuickCheapLLM
import okhttp3.OkHttpClient

/**
 * Factory for tier-specific GLM API instances backed by [GlmAnthropicCompatibleApi].
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
    ): DirectBudgetHighLLM = object : DirectBudgetHighLLM {
        private val delegate = buildDelegate(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
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
        private val delegate = buildDelegate(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
        override suspend fun call(request: ChatRequest): ChatResponse = delegate.call(request)
    }

    private fun buildDelegate(
        outFactory: OutFactory,
        httpClient: OkHttpClient,
        modelName: String,
        maxTokens: Int,
        apiEndpoint: String,
        apiToken: String,
    ) = GlmAnthropicCompatibleApi(outFactory, httpClient, modelName, maxTokens, apiEndpoint, apiToken)
}
