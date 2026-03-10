package com.glassthought.chainsaw.core.server

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Tests for [KtorHarnessServer].
 *
 * Uses a real Ktor CIO server bound to port 0 with OkHttp as the HTTP client.
 * This verifies the real port-file workflow end-to-end, which is why we do NOT
 * use Ktor's testApplication (it bypasses real port binding).
 */
class KtorHarnessServerTest : AsgardDescribeSpec({

    val httpClient = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    val jsonMediaType = "application/json".toMediaType()

    data class ServerFixture(
        val server: KtorHarnessServer,
        val portFilePath: Path,
    )

    fun createFixture(): ServerFixture {
        val tempDir = Files.createTempDirectory("harness-server-test")
        val portFilePath = tempDir.resolve("port.txt")
        val portFileManager = PortFileManager(portFilePath)
        val server = KtorHarnessServer(
            outFactory = outFactory,
            portFileManager = portFileManager,
        )
        return ServerFixture(server, portFilePath)
    }

    suspend fun withServer(block: suspend (ServerFixture) -> Unit) {
        val fixture = createFixture()
        fixture.server.start()
        try {
            block(fixture)
        } finally {
            fixture.server.close()
        }
    }

    fun postJson(port: Int, path: String, jsonBody: String): okhttp3.Response {
        val request = Request.Builder()
            .url("http://localhost:$port$path")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()
        return httpClient.newCall(request).execute()
    }

    describe("GIVEN a started KtorHarnessServer") {

        describe("AND port file management") {

            it("THEN port file exists after start") {
                withServer { fixture ->
                    Files.exists(fixture.portFilePath) shouldBe true
                }
            }

            it("THEN port file contains the actual bound port as a number") {
                withServer { fixture ->
                    val portFromFile = Files.readString(fixture.portFilePath).toInt()
                    portFromFile shouldBe fixture.server.port()
                }
            }

            it("THEN bound port is in valid TCP range (1-65535)") {
                withServer { fixture ->
                    fixture.server.port() shouldBeInRange 1..65535
                }
            }
        }

        describe("AND POST /agent/done is called with valid JSON") {

            it("THEN response status is 200") {
                withServer { fixture ->
                    val response = postJson(
                        fixture.server.port(),
                        "/agent/done",
                        """{"branch": "test-branch"}""",
                    )
                    response.use { it.code shouldBe 200 }
                }
            }
        }

        describe("AND POST /agent/question is called with valid JSON") {

            it("THEN response status is 200") {
                withServer { fixture ->
                    val response = postJson(
                        fixture.server.port(),
                        "/agent/question",
                        """{"branch": "test-branch", "question": "How do I proceed?"}""",
                    )
                    response.use { it.code shouldBe 200 }
                }
            }
        }

        describe("AND POST /agent/failed is called with valid JSON") {

            it("THEN response status is 200") {
                withServer { fixture ->
                    val response = postJson(
                        fixture.server.port(),
                        "/agent/failed",
                        """{"branch": "test-branch", "reason": "Out of memory"}""",
                    )
                    response.use { it.code shouldBe 200 }
                }
            }
        }

        describe("AND POST /agent/status is called with valid JSON") {

            it("THEN response status is 200") {
                withServer { fixture ->
                    val response = postJson(
                        fixture.server.port(),
                        "/agent/status",
                        """{"branch": "test-branch"}""",
                    )
                    response.use { it.code shouldBe 200 }
                }
            }
        }

        describe("AND POST /agent/done is called with malformed JSON") {

            it("THEN response status is 400") {
                withServer { fixture ->
                    val response = postJson(
                        fixture.server.port(),
                        "/agent/done",
                        """{"invalid json""",
                    )
                    response.use { it.code shouldBe 400 }
                }
            }
        }

        describe("AND POST /agent/done response body") {

            it("THEN response body is {\"status\":\"ok\"}") {
                withServer { fixture ->
                    val response = postJson(
                        fixture.server.port(),
                        "/agent/done",
                        """{"branch": "test-branch"}""",
                    )
                    response.use { it.body!!.string() shouldBe """{"status":"ok"}""" }
                }
            }
        }

        describe("AND the server is closed") {

            it("THEN port file is deleted after close") {
                val fixture = createFixture()
                fixture.server.start()
                try {
                    // server is running; close happens in finally to guarantee cleanup
                } finally {
                    fixture.server.close()
                }
                Files.exists(fixture.portFilePath) shouldBe false
            }
        }
    }
})
