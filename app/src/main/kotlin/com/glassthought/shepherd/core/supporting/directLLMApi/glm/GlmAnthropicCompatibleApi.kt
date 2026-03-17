package com.glassthought.shepherd.core.supporting.directLLMApi.glm

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.supporting.directLLMApi.ChatRequest
import com.glassthought.shepherd.core.supporting.directLLMApi.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Internal HTTP implementation for the Z.AI Anthropic-compatible API.
 *
 * Instances are created via [GlmDirectLlmFactory], which returns anonymous objects implementing
 * the appropriate tier interface ([com.glassthought.shepherd.core.supporting.directLLMApi.DirectBudgetHighLLM]
 * or [com.glassthought.shepherd.core.supporting.directLLMApi.DirectQuickCheapLLM]) that delegate
 * to this class. This class encapsulates the shared HTTP logic — request construction,
 * response parsing, and error handling.
 *
 * V1: single user message, no streaming, no retry logic.
 */
internal class GlmAnthropicCompatibleApi(
    outFactory: OutFactory,
    private val httpClient: OkHttpClient,
    private val modelName: String,
    private val maxTokens: Int,
    private val apiEndpoint: String,
    private val apiToken: String,
) {
    private val out = outFactory.getOutForClass(GlmAnthropicCompatibleApi::class)

    suspend fun call(request: ChatRequest): ChatResponse {
        out.info(
            "calling_direct_llm_api",
            Val(modelName, ValType.STRING_USER_AGNOSTIC),
            Val(apiEndpoint, ValType.SERVER_URL_USER_AGNOSTIC),
        )

        val requestBody = buildRequestBody(request.prompt)

        out.debug("direct_llm_api_request_body") {
            listOf(Val(requestBody, ValType.JSON_SERVER_REQUEST))
        }

        val httpRequest = Request.Builder()
            .url(apiEndpoint)
            .addHeader("x-api-key", apiToken)
            .addHeader("anthropic-version", Constants.Z_AI_API.ANTHROPIC_API_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        // OkHttp execute() is blocking; wrap in Dispatchers.IO.
        // V1 uses Dispatchers.IO directly; DispatcherProvider injection can be added
        // if this code needs to be used in a context requiring configurable dispatchers.
        val responseBody = withContext(Dispatchers.IO) {
            httpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string()
                    ?: throw IllegalStateException(
                        "Direct LLM API returned null body. HTTP status=[${response.code}]"
                    )

                if (!response.isSuccessful) {
                    val bodySnippet = body.take(MAX_ERROR_BODY_SNIPPET_LENGTH)
                    throw IllegalStateException(
                        "Direct LLM API returned non-2xx status. " +
                            "HTTP status=[${response.code}], body_snippet=[$bodySnippet]"
                    )
                }

                body
            }
        }

        val text = parseResponseContent(responseBody)

        out.info(
            "direct_llm_api_response_received",
            Val(modelName, ValType.STRING_USER_AGNOSTIC),
        )

        out.debug("direct_llm_api_response_text") {
            listOf(Val(text, ValType.SERVER_RESPONSE_BODY))
        }

        return ChatResponse(text)
    }

    private fun buildRequestBody(prompt: String): String {
        return JSONObject().apply {
            put("model", modelName)
            put("max_tokens", maxTokens)
            put(
                "messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
            )
        }.toString()
    }

    /**
     * Extracts `content[0].text` from the Anthropic-compatible API response JSON.
     *
     * @throws IllegalStateException if the response structure is unexpected.
     */
    private fun parseResponseContent(responseBody: String): String {
        try {
            val json = JSONObject(responseBody)
            val content = json.getJSONArray("content")

            if (content.length() == 0) {
                throw IllegalStateException(
                    "Direct LLM API returned empty content array. response_snippet=[${responseBody.take(MAX_ERROR_BODY_SNIPPET_LENGTH)}]"
                )
            }

            val firstBlock = content.getJSONObject(0)

            // Verify it's a text block
            if (firstBlock.getString("type") != "text") {
                throw IllegalStateException(
                    "Direct LLM API returned non-text content block. type=[${firstBlock.getString("type")}], response_snippet=[${responseBody.take(MAX_ERROR_BODY_SNIPPET_LENGTH)}]"
                )
            }

            return firstBlock.getString("text")
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse direct LLM API response. response_snippet=[${responseBody.take(MAX_ERROR_BODY_SNIPPET_LENGTH)}]",
                e
            )
        }
    }

    companion object {
        private const val MAX_ERROR_BODY_SNIPPET_LENGTH = 500
    }
}
