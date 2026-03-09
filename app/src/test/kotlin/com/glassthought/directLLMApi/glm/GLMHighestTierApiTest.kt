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
    val apiToken = "test-token-123"

    fun successResponseJson(content: String): String {
        return JSONObject().apply {
            put("choices", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", content)
                    })
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
            apiEndpoint = server.url("/chat/completions").toString(),
            apiToken = apiToken,
        )
        return TestFixture(server, api)
    }

    describe("GIVEN GLMHighestTierApi with MockWebServer") {

        describe("WHEN call is made with a simple prompt") {
            val prompt = "What is 2+2?"
            val expectedResponse = "4"

            it("THEN request method is POST") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                fixture.api.call(ChatRequest(prompt))

                val recorded = fixture.server.takeRequest()
                recorded.method shouldBe "POST"
                fixture.server.shutdown()
            }

            it("THEN request has correct Authorization header") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                fixture.api.call(ChatRequest(prompt))

                val recorded = fixture.server.takeRequest()
                recorded.getHeader("Authorization") shouldBe "Bearer $apiToken"
                fixture.server.shutdown()
            }

            it("THEN request has correct Content-Type header") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                fixture.api.call(ChatRequest(prompt))

                val recorded = fixture.server.takeRequest()
                recorded.getHeader("Content-Type") shouldBe "application/json; charset=utf-8"
                fixture.server.shutdown()
            }

            it("THEN request body contains the model name") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                fixture.api.call(ChatRequest(prompt))

                val recorded = fixture.server.takeRequest()
                val body = JSONObject(recorded.body.readUtf8())
                body.getString("model") shouldBe modelName
                fixture.server.shutdown()
            }

            it("THEN request body contains the prompt as user message") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                fixture.api.call(ChatRequest(prompt))

                val recorded = fixture.server.takeRequest()
                val body = JSONObject(recorded.body.readUtf8())
                val messages = body.getJSONArray("messages")
                messages.length() shouldBe 1

                val message = messages.getJSONObject(0)
                message.getString("role") shouldBe "user"
                message.getString("content") shouldBe prompt
                fixture.server.shutdown()
            }

            it("THEN response text matches the mock response content") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson(expectedResponse)))
                val response = fixture.api.call(ChatRequest(prompt))

                response.text shouldBe expectedResponse
                fixture.server.shutdown()
            }
        }

        describe("WHEN prompt contains special characters needing JSON escaping") {
            val prompt = "He said \"hello\\nworld\"\nNew line here\tand a tab"

            it("THEN request body is valid JSON with correctly escaped content") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson("ok")))
                fixture.api.call(ChatRequest(prompt))

                val recorded = fixture.server.takeRequest()
                val body = JSONObject(recorded.body.readUtf8())
                val content = body.getJSONArray("messages")
                    .getJSONObject(0)
                    .getString("content")
                content shouldBe prompt
                fixture.server.shutdown()
            }

            it("THEN response is returned successfully") {
                val fixture = createFixture()
                fixture.server.enqueue(MockResponse().setBody(successResponseJson("ok")))
                val response = fixture.api.call(ChatRequest(prompt))

                response.text shouldBe "ok"
                fixture.server.shutdown()
            }
        }

        describe("WHEN API returns non-2xx status") {
            it("THEN throws IllegalStateException with status code information") {
                val fixture = createFixture()
                fixture.server.enqueue(
                    MockResponse()
                        .setResponseCode(500)
                        .setBody("""{"error": "internal server error"}""")
                )

                val exception = shouldThrow<IllegalStateException> {
                    fixture.api.call(ChatRequest("test"))
                }
                exception.message shouldContain "500"
                fixture.server.shutdown()
            }
        }

        describe("WHEN API returns malformed JSON") {
            it("THEN throws IllegalStateException indicating parse failure") {
                val fixture = createFixture()
                fixture.server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("this is not json")
                )

                val exception = shouldThrow<IllegalStateException> {
                    fixture.api.call(ChatRequest("test"))
                }
                exception.message shouldContain "Failed to parse"
                fixture.server.shutdown()
            }
        }

        describe("WHEN API returns empty choices array") {
            it("THEN throws IllegalStateException mentioning empty choices") {
                val fixture = createFixture()
                fixture.server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"choices": []}""")
                )

                val exception = shouldThrow<IllegalStateException> {
                    fixture.api.call(ChatRequest("test"))
                }
                exception.message shouldContain "empty choices"
                fixture.server.shutdown()
            }
        }
    }
})
