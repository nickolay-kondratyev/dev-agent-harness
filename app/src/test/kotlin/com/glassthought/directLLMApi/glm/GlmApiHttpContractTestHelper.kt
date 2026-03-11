package com.glassthought.directLLMApi.glm

import com.asgard.core.out.OutFactory
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.ChatRequest
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.DirectLLM
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP contract tests for all GLM Anthropic-compatible API implementations.
 *
 * Called from each concrete test class ([GLMHighestTierApiTest], [GLMQuickCheapApiTest])
 * to avoid duplicating the 14 HTTP behavior tests. The tier-specific type-check test stays
 * in each concrete test class.
 *
 * @param modelName The model name string expected in the request body.
 * @param createApi Factory lambda receiving the test parameters and returning a [DirectLLM]
 *   instance under test.
 */
fun AsgardDescribeSpec.glmApiHttpContractTests(
    modelName: String,
    createApi: (outFactory: OutFactory, httpClient: OkHttpClient, modelName: String, maxTokens: Int, apiEndpoint: String, apiToken: String) -> DirectLLM,
) {
    val httpClient = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    val maxTokens = 4096
    val apiToken = "test-token-123"

    fun successResponseJson(content: String): String {
        return JSONObject().apply {
            put("content", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", content)
                })
            })
        }.toString()
    }

    data class TestFixture(
        val server: MockWebServer,
        val api: DirectLLM,
    )

    fun createFixture(): TestFixture {
        val server = MockWebServer()
        server.start()
        val api = createApi(
            outFactory,
            httpClient,
            modelName,
            maxTokens,
            server.url("/chat/completions").toString(),
            apiToken,
        )
        return TestFixture(server, api)
    }

    suspend fun withFixture(block: suspend (TestFixture) -> Unit) {
        val fixture = createFixture()
        try {
            block(fixture)
        } finally {
            fixture.server.shutdown()
        }
    }

    describe("GIVEN GLM API with MockWebServer") {

        describe("WHEN call is made with a simple prompt") {
            val prompt = "What is 2+2?"
            val expectedResponse = "4"

            it("THEN request method is POST") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    recorded.method shouldBe "POST"
                }
            }

            it("THEN request has correct x-api-key header") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    recorded.getHeader("x-api-key") shouldBe apiToken
                }
            }

            it("THEN request has correct Content-Type header") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    recorded.getHeader("Content-Type") shouldBe "application/json; charset=utf-8"
                }
            }

            it("THEN request body contains the model name") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    body.getString("model") shouldBe modelName
                }
            }

            it("THEN request body has a max_tokens field") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    body.has("max_tokens") shouldBe true
                }
            }

            it("THEN request body max_tokens value matches configured value") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    body.getInt("max_tokens") shouldBe maxTokens
                }
            }

            it("THEN request body has exactly one message") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    val messages = body.getJSONArray("messages")
                    messages.length() shouldBe 1
                }
            }

            it("THEN request body message role is user") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    val message = body.getJSONArray("messages").getJSONObject(0)
                    message.getString("role") shouldBe "user"
                }
            }

            it("THEN request body message content matches prompt") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    val message = body.getJSONArray("messages").getJSONObject(0)
                    message.getString("content") shouldBe prompt
                }
            }

            it("THEN response text matches the mock response content") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    val response = fixture.api.call(ChatRequest(prompt))

                    response.text shouldBe expectedResponse
                }
            }
        }

        describe("WHEN prompt contains special characters needing JSON escaping") {
            val prompt = "He said \"hello\\nworld\"\nNew line here\tand a tab"

            it("THEN request body is valid JSON with correctly escaped content") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson("ok")))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    val content = body.getJSONArray("messages")
                        .getJSONObject(0)
                        .getString("content")
                    content shouldBe prompt
                }
            }

            it("THEN response is returned successfully") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson("ok")))
                    val response = fixture.api.call(ChatRequest(prompt))

                    response.text shouldBe "ok"
                }
            }
        }

        describe("WHEN API returns non-2xx status") {
            it("THEN throws IllegalStateException with status code information") {
                withFixture { fixture ->
                    fixture.server.enqueue(
                        MockResponse()
                            .setResponseCode(500)
                            .setBody("""{"error": "internal server error"}""")
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        fixture.api.call(ChatRequest("test"))
                    }
                    exception.message shouldContain "500"
                }
            }
        }

        describe("WHEN API returns malformed JSON") {
            it("THEN throws IllegalStateException indicating parse failure") {
                withFixture { fixture ->
                    fixture.server.enqueue(
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("this is not json")
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        fixture.api.call(ChatRequest("test"))
                    }
                    exception.message shouldContain "Failed to parse"
                }
            }
        }

        describe("WHEN API returns empty content array") {
            it("THEN throws IllegalStateException mentioning empty content") {
                withFixture { fixture ->
                    fixture.server.enqueue(
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"content": []}""")
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        fixture.api.call(ChatRequest("test"))
                    }
                    exception.message shouldContain "empty content"
                }
            }
        }
    }
}
