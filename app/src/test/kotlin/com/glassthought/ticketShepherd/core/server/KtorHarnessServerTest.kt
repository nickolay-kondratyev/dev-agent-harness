package com.glassthought.ticketShepherd.core.server

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
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
            portPublisher = portFileManager,
            agentRequestHandler = NoOpAgentRequestHandler(),
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

    describe("GIVEN a KtorHarnessServer that has not been started") {

        it("THEN port() throws IllegalStateException") {
            val fixture = createFixture()
            shouldThrow<IllegalStateException> { fixture.server.port() }
        }
    }

    describe("GIVEN a started KtorHarnessServer") {

        describe("AND start() is called a second time") {

            it("THEN throws IllegalStateException") {
                withServer { fixture ->
                    shouldThrow<IllegalStateException> { fixture.server.start() }
                }
            }
        }

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

            it("THEN port() throws IllegalStateException after close") {
                val fixture = createFixture()
                fixture.server.start()
                try { } finally { fixture.server.close() }
                shouldThrow<IllegalStateException> { fixture.server.port() }
            }

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

            it("THEN second close() does not throw (idempotent)") {
                val fixture = createFixture()
                fixture.server.start()
                fixture.server.close()
                shouldNotThrow<Exception> { fixture.server.close() }
            }
        }
    }

    describe("GIVEN a KtorHarnessServer with a recording handler") {

        class RecordingAgentRequestHandler(
            private val questionAnswer: String = "test-answer",
        ) : AgentRequestHandler {
            val doneCalls = mutableListOf<AgentDoneRequest>()
            val failedCalls = mutableListOf<AgentFailedRequest>()
            val statusCalls = mutableListOf<AgentStatusRequest>()

            override suspend fun onDone(request: AgentDoneRequest) { doneCalls.add(request) }
            override suspend fun onQuestion(request: AgentQuestionRequest): String = questionAnswer
            override suspend fun onFailed(request: AgentFailedRequest) { failedCalls.add(request) }
            override suspend fun onStatus(request: AgentStatusRequest) { statusCalls.add(request) }
        }

        data class RecordingFixture(
            val server: ServerFixture,
            val handler: RecordingAgentRequestHandler,
        )

        fun createRecordingFixture(questionAnswer: String = "test-answer"): RecordingFixture {
            val tempDir = Files.createTempDirectory("harness-server-test")
            val portFilePath = tempDir.resolve("port.txt")
            val handler = RecordingAgentRequestHandler(questionAnswer)
            val server = KtorHarnessServer(
                outFactory = outFactory,
                portPublisher = PortFileManager(portFilePath),
                agentRequestHandler = handler,
            )
            return RecordingFixture(ServerFixture(server, portFilePath), handler)
        }

        suspend fun withRecordingServer(
            questionAnswer: String = "test-answer",
            block: suspend (ServerFixture, RecordingAgentRequestHandler) -> Unit,
        ) {
            val (fixture, handler) = createRecordingFixture(questionAnswer)
            fixture.server.start()
            try {
                block(fixture, handler)
            } finally {
                fixture.server.close()
            }
        }

        describe("AND POST /agent/done is called") {

            it("THEN onDone is invoked with the correct branch") {
                withRecordingServer { fixture, handler ->
                    postJson(fixture.server.port(), "/agent/done", """{"branch":"my-branch"}""").close()
                    handler.doneCalls.size shouldBe 1
                }
            }

            it("THEN onDone receives the correct branch value") {
                withRecordingServer { fixture, handler ->
                    postJson(fixture.server.port(), "/agent/done", """{"branch":"my-branch"}""").close()
                    handler.doneCalls[0].branch shouldBe "my-branch"
                }
            }
        }

        describe("AND POST /agent/question is called") {

            it("THEN response body contains the handler's answer") {
                withRecordingServer(questionAnswer = "the answer") { fixture, _ ->
                    val response = postJson(
                        fixture.server.port(),
                        "/agent/question",
                        """{"branch":"my-branch","question":"What now?"}""",
                    )
                    response.use { it.body!!.string() shouldBe """{"answer":"the answer"}""" }
                }
            }
        }
    }
})
