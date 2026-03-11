package com.glassthought.ticketShepherd.core.supporting.directLLMApi.glm

import com.asgard.core.out.OutFactory
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.ChatRequest
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.ChatResponse
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.DirectQuickCheapLLM
import okhttp3.OkHttpClient

/**
 * [DirectQuickCheapLLM] implementation for Z.AI's GLM quick/cheap-tier model.
 *
 * Delegates all HTTP logic to [GlmAnthropicCompatibleApi].
 *
 * @param outFactory Logging factory.
 * @param httpClient Shared OkHttp client instance. Callers should reuse the returned
 *   [DirectQuickCheapLLM] instance rather than calling the factory repeatedly, because OkHttp
 *   recommends a single shared client for connection pooling.
 * @param modelName The model identifier to send in the API request (e.g. "glm-4.7-flash").
 * @param maxTokens Maximum tokens for the response (required by Anthropic API).
 * @param apiEndpoint The Anthropic-compatible endpoint URL.
 * @param apiToken API key for authentication (sent as x-api-key header).
 */
class GLMQuickCheapApi(
    outFactory: OutFactory,
    httpClient: OkHttpClient,
    modelName: String,
    maxTokens: Int,
    apiEndpoint: String,
    apiToken: String,
) : DirectQuickCheapLLM {

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
