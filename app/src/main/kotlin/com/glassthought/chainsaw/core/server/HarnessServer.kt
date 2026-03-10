package com.glassthought.chainsaw.core.server

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.lifecycle.AsgardCloseable
import com.asgard.core.out.OutFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * HTTP server for agent-to-harness communication.
 *
 * Agents call endpoints via harness-cli-for-agent.sh (ref.ap.8PB8nMd93D3jipEWhME5n.E).
 * The server binds to an OS-assigned port and publishes it via a port file.
 */
interface HarnessServer : AsgardCloseable {
    /** Starts the server, binds to a port, and writes the port file. */
    suspend fun start()

    /** Returns the bound port. Throws if the server has not been started. */
    fun port(): Int
}

/**
 * Ktor CIO implementation of [HarnessServer].
 *
 * Binds to port 0 (OS-assigned), writes the resolved port to the port file via
 * [PortFileManager], and exposes 4 stub POST endpoints under `/agent/`.
 *
 * @param outFactory Logging factory for structured logging.
 * @param portFileManager Manages writing/deleting the port file.
 */
class KtorHarnessServer(
    outFactory: OutFactory,
    private val portFileManager: PortFileManager,
) : HarnessServer {

    private val out = outFactory.getOutForClass(KtorHarnessServer::class)
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var boundPort: Int? = null

    override suspend fun start() {
        check(engine == null) { "Server already started" }

        val server = embeddedServer(CIO, port = 0) {
            configureServer()
        }

        server.start(wait = false)

        val resolvedPort = server.engine.resolvedConnectors().first().port
        portFileManager.writePort(resolvedPort)

        engine = server
        boundPort = resolvedPort

        out.info(
            "harness_server_started",
            Val(resolvedPort, ValType.PORT_AS_INT),
        )
    }

    override fun port(): Int {
        return boundPort ?: throw IllegalStateException("Server has not been started")
    }

    override suspend fun close() {
        val currentEngine = engine ?: return

        currentEngine.stop(
            gracePeriodMillis = GRACEFUL_SHUTDOWN_PERIOD_MILLIS,
            timeoutMillis = SHUTDOWN_TIMEOUT_MILLIS,
        )
        portFileManager.deletePort()

        engine = null
        boundPort = null

        out.info("harness_server_stopped")
    }

    private fun Application.configureServer() {
        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule.Builder().build())
            }
        }

        routing {
            route("/agent") {
                post("/done") {
                    val request = call.receive<AgentDoneRequest>()
                    out.info(
                        "agent_request_received",
                        Val("/agent/done", ValType.HTTP_REQUEST_PATH),
                        Val(request.branch, ValType.GIT_BRANCH_NAME),
                    )
                    call.respond(mapOf("status" to "ok"))
                }

                post("/question") {
                    val request = call.receive<AgentQuestionRequest>()
                    out.info(
                        "agent_request_received",
                        Val("/agent/question", ValType.HTTP_REQUEST_PATH),
                        Val(request.branch, ValType.GIT_BRANCH_NAME),
                    )
                    call.respond(mapOf("status" to "ok"))
                }

                post("/failed") {
                    val request = call.receive<AgentFailedRequest>()
                    out.info(
                        "agent_request_received",
                        Val("/agent/failed", ValType.HTTP_REQUEST_PATH),
                        Val(request.branch, ValType.GIT_BRANCH_NAME),
                    )
                    call.respond(mapOf("status" to "ok"))
                }

                post("/status") {
                    val request = call.receive<AgentStatusRequest>()
                    out.info(
                        "agent_request_received",
                        Val("/agent/status", ValType.HTTP_REQUEST_PATH),
                        Val(request.branch, ValType.GIT_BRANCH_NAME),
                    )
                    call.respond(mapOf("status" to "ok"))
                }
            }
        }
    }

    companion object {
        private const val GRACEFUL_SHUTDOWN_PERIOD_MILLIS = 1000L
        private const val SHUTDOWN_TIMEOUT_MILLIS = 5000L
    }
}
