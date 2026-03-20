package com.glassthought.shepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.creator.TicketShepherdCreator
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.server.ShepherdServer
import com.glassthought.shepherd.core.session.SessionsState
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import java.nio.file.Path

/**
 * CLI input parameters for [ShepherdInitializer].
 *
 * Parsed by picocli in [com.glassthought.shepherd.cli.AppMain] and passed here
 * as a clean data structure (no picocli annotations leak into the core).
 */
data class CliParams(
    val ticketPath: Path,
    val workflowName: String,
    val iterationMax: Int,
)

/**
 * Orchestrates the full startup sequence for the shepherd harness.
 *
 * Steps (per spec ref.ap.HRlQHC1bgrTRyRknP3WNX.E):
 * 1. [ContextInitializer.initialize] -> [ShepherdContext]
 * 2. [ShepherdServer] startup (Ktor CIO on [Constants.AGENT_COMM.SERVER_PORT_ENV_VAR])
 * 3. [TicketShepherdCreator.create] -> [TicketShepherd]
 * 4. [TicketShepherd.run] — drives workflow (never returns, exits process)
 *
 * Cleanup closes resources in reverse order from the latest successful step.
 *
 * ap.mFo35x06vJbjMQ8m7Lh4Z.E
 */
@AnchorPoint("ap.mFo35x06vJbjMQ8m7Lh4Z.E")
class ShepherdInitializer(
    private val outFactory: OutFactory,
    private val contextInitializer: ContextInitializer,
    private val ticketShepherdCreatorFactory: TicketShepherdCreatorFactory,
    private val serverPortReader: () -> Int = ::readServerPortFromEnv,
    private val serverStarter: ServerStarter = KtorServerStarter,
) {

    /**
     * Runs the full startup sequence and drives the workflow to completion.
     *
     * On success, [com.glassthought.shepherd.core.TicketShepherd.run] exits the process.
     * On failure at any step, resources are cleaned up in reverse order and the exception propagates.
     */
    suspend fun run(cliParams: CliParams) {
        // Step 1: Initialize shared infrastructure -> ShepherdContext
        val shepherdContext = contextInitializer.initialize(outFactory)

        try {
            // Step 2: Start embedded HTTP server for agent callbacks
            val sessionsState = SessionsState()
            val shepherdServer = ShepherdServer(sessionsState, outFactory)
            val serverPort = serverPortReader()
            val ktorServer = serverStarter.start(shepherdServer, serverPort)

            try {
                // Step 3: Create TicketShepherd with all ticket-scoped wiring
                // DEFERRED: thread cliParams.iterationMax to TicketShepherdCreator.create() when its
                //  signature supports it. Currently iterationMax is parsed from CLI but not yet
                //  consumed downstream. See ref.ap.mFo35x06vJbjMQ8m7Lh4Z.E.
                val ticketShepherd = ticketShepherdCreatorFactory
                    .create(outFactory)
                    .create(
                        shepherdContext = shepherdContext,
                        ticketPath = cliParams.ticketPath,
                        workflowName = cliParams.workflowName,
                    )

                // Step 4: Drive workflow (never returns — exits process)
                ticketShepherd.run()
            } finally {
                ktorServer.stop(
                    gracePeriodMillis = KTOR_STOP_GRACE_PERIOD_MS,
                    timeoutMillis = KTOR_STOP_TIMEOUT_MS,
                )
            }
        } finally {
            shepherdContext.close()
        }
    }

    companion object {
        private const val KTOR_STOP_GRACE_PERIOD_MS = 1000L
        private const val KTOR_STOP_TIMEOUT_MS = 2000L

        /**
         * Reads the server port from the [Constants.AGENT_COMM.SERVER_PORT_ENV_VAR] env var.
         *
         * @param envProvider supplier for env var values; defaults to [System.getenv].
         *   Injectable for testing to avoid ambient shell state.
         * @throws IllegalStateException if the env var is missing or not a valid port number.
         */
        fun readServerPortFromEnv(envProvider: (String) -> String? = System::getenv): Int {
            val envVar = Constants.AGENT_COMM.SERVER_PORT_ENV_VAR
            val portStr = envProvider(envVar)
                ?: error("Environment variable [$envVar] is not set. " +
                    "It must specify the port for the embedded HTTP server.")

            return portStr.toIntOrNull()
                ?: error("Environment variable [$envVar] has invalid value [$portStr]. " +
                    "It must be a valid port number.")
        }
    }
}

/**
 * Abstraction for starting the Ktor embedded server.
 * Extracted for testability — tests can avoid actually starting a server.
 */
fun interface ServerStarter {
    fun start(shepherdServer: ShepherdServer, port: Int): StoppableServer
}

/**
 * Minimal interface for stopping the Ktor server during cleanup.
 * Avoids leaking Ktor types into the test surface.
 */
fun interface StoppableServer {
    fun stop(gracePeriodMillis: Long, timeoutMillis: Long)
}

/**
 * Production [ServerStarter] that launches a Ktor CIO embedded server.
 */
object KtorServerStarter : ServerStarter {
    override fun start(shepherdServer: ShepherdServer, port: Int): StoppableServer {
        val engine = embeddedServer(CIO, port = port) {
            shepherdServer.configureApplication(this)
        }.start(wait = false)

        return StoppableServer { gracePeriodMillis, timeoutMillis ->
            engine.stop(gracePeriodMillis, timeoutMillis)
        }
    }
}

/**
 * Factory for creating [TicketShepherdCreator] with production wiring.
 * Extracted so tests can inject a fake creator without needing real ProcessRunner, git, etc.
 */
fun interface TicketShepherdCreatorFactory {
    fun create(outFactory: OutFactory): TicketShepherdCreator
}
