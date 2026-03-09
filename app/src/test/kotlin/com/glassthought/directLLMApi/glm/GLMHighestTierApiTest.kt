package com.glassthought.directLLMApi.glm

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.directLLMApi.ChatRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [GLMHighestTierApi] using MockWebServer to verify
 * HTTP request construction and response parsing without real network calls.
 */
class GLMHighestTierApiTest : AsgardDescribeSpec({

    val httpClient = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    val modelName = "glm-5"
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

    /**
     * Creates a fresh MockWebServer and GLMHighestTierApi for each test.
     * Returns both so the test can enqueue responses and inspect recorded requests.
     */
    data class TestFixture(
        val server: MockWebServer,
        val api: GLMHighestTierApi,
    )

    fun createFixture(): TestFixture {
        val server = MockWebServer()
        server.start()
        val api = GLMHighestTierApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = modelName,
            maxTokens = maxTokens,
            apiEndpoint = server.url("/chat/completions").toString(),
            apiToken = apiToken,
        )
        return TestFixture(server, api)
    }

    /**
     * Runs a test block with a [TestFixture], ensuring the MockWebServer
     * is shut down even if assertions fail.
     */
    suspend fun withFixture(block: suspend (TestFixture) -> Unit) {
        val fixture = createFixture()
        try {
            block(fixture)
        } finally {
            fixture.server.shutdown()
        }
    }

    describe("GIVEN GLMHighestTierApi with MockWebServer") {

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

            it("THEN request body contains max_tokens parameter") {
                withFixture { fixture ->
                    fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                    fixture.api.call(ChatRequest(prompt))

                    val recorded = fixture.server.takeRequest()
                    val body = JSONObject(recorded.body.readUtf8())
                    body.has("max_tokens") shouldBe true
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
})
