package com.glassthought.directLLMApi.glm

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.directLLMApi.ChatRequest
import com.glassthought.directLLMApi.ChatResponse
import com.glassthought.directLLMApi.DirectLLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * [DirectLLM] implementation for Z.AI's GLM highest-tier model.
 *
 * Uses the OpenAI-compatible chat completions API format.
 * V1: single user message, no streaming, no retry logic.
 *
 * @param outFactory Logging factory.
 * @param httpClient Shared OkHttp client instance. Callers should reuse the returned
 *   [DirectLLM] instance rather than calling the factory repeatedly, because OkHttp
 *   recommends a single shared client for connection pooling.
 * @param modelName The model identifier to send in the API request (e.g. "glm-5").
 * @param apiEndpoint The chat completions endpoint URL.
 * @param apiToken Bearer token for API authentication.
 */
class GLMHighestTierApi(
    outFactory: OutFactory,
    private val httpClient: OkHttpClient,
    private val modelName: String,
    private val apiEndpoint: String,
    private val apiToken: String,
) : DirectLLM {

    private val out = outFactory.getOutForClass(GLMHighestTierApi::class)

    override suspend fun call(request: ChatRequest): ChatResponse {
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
            .addHeader("Authorization", "Bearer $apiToken")
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
     * Extracts `choices[0].message.content` from the API response JSON.
     *
     * @throws IllegalStateException if the response structure is unexpected.
     */
    private fun parseResponseContent(responseBody: String): String {
        try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")

            if (choices.length() == 0) {
                throw IllegalStateException(
                    "Direct LLM API returned empty choices array. response_snippet=[${responseBody.take(MAX_ERROR_BODY_SNIPPET_LENGTH)}]"
                )
            }

            return choices
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
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
