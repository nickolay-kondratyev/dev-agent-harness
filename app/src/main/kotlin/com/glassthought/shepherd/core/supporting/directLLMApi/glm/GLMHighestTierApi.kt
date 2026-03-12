package com.glassthought.shepherd.core.supporting.directLLMApi.glm

import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.supporting.directLLMApi.ChatRequest
import com.glassthought.shepherd.core.supporting.directLLMApi.ChatResponse
import com.glassthought.shepherd.core.supporting.directLLMApi.DirectBudgetHighLLM
import okhttp3.OkHttpClient

/**
 * [DirectBudgetHighLLM] implementation for Z.AI's GLM highest-tier model.
 *
 * Delegates all HTTP logic to [GlmAnthropicCompatibleApi].
 *
 * @param outFactory Logging factory.
 * @param httpClient Shared OkHttp client instance. Callers should reuse the returned
 *   [DirectBudgetHighLLM] instance rather than calling the factory repeatedly, because OkHttp
 *   recommends a single shared client for connection pooling.
 * @param modelName The model identifier to send in the API request (e.g. "glm-5").
 * @param maxTokens Maximum tokens for the response (required by Anthropic API).
 * @param apiEndpoint The Anthropic-compatible endpoint URL.
 * @param apiToken API key for authentication (sent as x-api-key header).
 */
class GLMHighestTierApi(
    outFactory: OutFactory,
    httpClient: OkHttpClient,
    modelName: String,
    maxTokens: Int,
    apiEndpoint: String,
    apiToken: String,
) : DirectBudgetHighLLM {

    private val delegate = GlmAnthropicCompatibleApi(
        outFactory = outFactory,
        httpClient = httpClient,
        modelName = modelName,
        maxTokens = maxTokens,
        apiEndpoint = apiEndpoint,
        apiToken = apiToken,
    )

    override suspend fun call(request: ChatRequest): ChatResponse = delegate.call(request)
}
