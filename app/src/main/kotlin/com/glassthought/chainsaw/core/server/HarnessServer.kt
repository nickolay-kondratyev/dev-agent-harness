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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * HTTP server for agent-to-harness communication. ap.NAVMACFCbnE7L6Geutwyk.E
 *
 * Agents call endpoints via a CLI script (script removed; will be rebuilt per updated spec).
 * The server binds to an OS-assigned port and publishes it via a port file.
 *
 * See design: ref.ap.7sZveqPcid5z1ntmLs27UqN6.E (Agent↔Harness Communication section)
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
 * [PortPublisher], and exposes 4 POST endpoints under `/agent/`.
 *
 * HTTP protocol concerns (parsing, routing, responding) live here. Business logic
 * is delegated to [agentRequestHandler], which the phase runner wires in.
 *
 * @param outFactory Logging factory for structured logging.
 * @param portPublisher Publishes/removes the port file so agents can discover the server.
 * @param agentRequestHandler Handles agent requests; use [NoOpAgentRequestHandler] as a placeholder.
 */
class KtorHarnessServer(
    outFactory: OutFactory,
    private val portPublisher: PortPublisher,
    private val agentRequestHandler: AgentRequestHandler = NoOpAgentRequestHandler(),
) : HarnessServer {

    private val out = outFactory.getOutForClass(KtorHarnessServer::class)
    private val lifecycleMutex = Mutex()
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var boundPort: Int? = null

    override suspend fun start() {
        lifecycleMutex.withLock {
            check(engine == null) { "Server already started" }

            val server = embeddedServer(CIO, port = 0) {
                configureServer()
            }

            server.start(wait = false)
            engine = server  // assign early so close() can stop the server if writePort throws

            val resolvedPort = server.engine.resolvedConnectors().first().port
            try {
                portPublisher.writePort(resolvedPort)
                boundPort = resolvedPort
            } catch (e: Exception) {
                // Must stop server outside mutex lock to avoid deadlock if close() is called
                // from another context. Set engine to null first so close() becomes no-op.
                val engineToStop = engine
                engine = null
                boundPort = null
                engineToStop?.stop(
                    gracePeriodMillis = GRACEFUL_SHUTDOWN_PERIOD_MILLIS,
                    timeoutMillis = SHUTDOWN_TIMEOUT_MILLIS,
                )
                throw e
            }

            out.info(
                "harness_server_started",
                Val(resolvedPort, ValType.PORT_AS_INT),
            )
        }
    }

    override fun port(): Int {
        return boundPort ?: throw IllegalStateException("Server has not been started")
    }

    override suspend fun close() {
        val currentEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>?
        lifecycleMutex.withLock {
            currentEngine = engine
            engine = null
            boundPort = null
        }

        // Stop server outside mutex to avoid blocking other operations during shutdown
        currentEngine ?: return

        currentEngine.stop(
            gracePeriodMillis = GRACEFUL_SHUTDOWN_PERIOD_MILLIS,
            timeoutMillis = SHUTDOWN_TIMEOUT_MILLIS,
        )
        portPublisher.deletePort()

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
                    handleAgentRequest<AgentDoneRequest>("/agent/done") { req ->
                        agentRequestHandler.onDone(req)
                        OK_RESPONSE
                    }
                }
                post("/question") {
                    handleAgentRequest<AgentQuestionRequest>("/agent/question") { req ->
                        mapOf("answer" to agentRequestHandler.onQuestion(req))
                    }
                }
                post("/failed") {
                    handleAgentRequest<AgentFailedRequest>("/agent/failed") { req ->
                        agentRequestHandler.onFailed(req)
                        OK_RESPONSE
                    }
                }
                post("/status") {
                    handleAgentRequest<AgentStatusRequest>("/agent/status") { req ->
                        agentRequestHandler.onStatus(req)
                        OK_RESPONSE
                    }
                }
            }
        }
    }

    /**
     * Receives an [AgentRequest] of type [T], logs it, invokes [action], and responds with
     * the result.
     *
     * Consolidates the common receive-log-respond pattern shared by all agent endpoints.
     * The caller supplies [action] to produce the response body, keeping HTTP concerns here
     * while delegating business logic to [AgentRequestHandler].
     */
    private suspend inline fun <reified T : AgentRequest> RoutingContext.handleAgentRequest(
        path: String,
        action: suspend (T) -> Any,
    ) {
        val request = call.receive<T>()
        out.info(
            "agent_request_received",
            Val(path, ValType.HTTP_REQUEST_PATH),
            Val(request.branch, ValType.GIT_BRANCH_NAME),
        )
        val response = action(request)
        call.respond(response)
    }

    companion object {
        private const val GRACEFUL_SHUTDOWN_PERIOD_MILLIS = 1000L
        private const val SHUTDOWN_TIMEOUT_MILLIS = 5000L
        private val OK_RESPONSE = mapOf("status" to "ok")
    }
}
