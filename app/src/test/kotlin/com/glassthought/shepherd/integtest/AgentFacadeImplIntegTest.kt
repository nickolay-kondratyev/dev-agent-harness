package com.glassthought.shepherd.integtest

import com.glassthought.bucket.isIntegTestEnabled
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.contextwindow.ContextWindowStateReader
import com.glassthought.shepherd.core.agent.facade.AgentFacadeImpl
import com.glassthought.shepherd.core.agent.facade.AgentPayload
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.ContextWindowState
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.SpawnAgentConfig
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.data.HealthTimeoutLadder
import com.glassthought.shepherd.core.question.QaDrainer
import com.glassthought.shepherd.core.server.AckedPayloadSenderImpl
import com.glassthought.shepherd.core.server.ShepherdServer
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.time.SystemClock
import com.glassthought.shepherd.usecase.healthmonitoring.AgentUnresponsiveUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end integration test for [AgentFacadeImpl] signal flow.
 *
 * Validates the full chain:
 * 1. Start a real Ktor CIO HTTP server ([ShepherdServer])
 * 2. Construct [AgentFacadeImpl] with real infrastructure
 * 3. Spawn a GLM-backed agent that calls `callback_shepherd.signal.sh` scripts
 * 4. Verify the spawned handle, signal delivery, and session lifecycle
 *
 * Requires: tmux installed, `Z_AI_GLM_API_TOKEN` secret available, `-PrunIntegTests=true`.
 */
@OptIn(ExperimentalKotest::class)
class AgentFacadeImplIntegTest : SharedContextDescribeSpec({

    describe("GIVEN AgentFacadeImpl with real server and GLM agent").config(isIntegTestEnabled()) {

        // ── Shared infrastructure from ShepherdContext ──────────────────────
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val realAdapter = shepherdContext.infra.claudeCode.agentTypeAdapter
        val outFactory = shepherdContext.infra.outFactory

        require(realAdapter is ClaudeCodeAdapter) {
            "Expected ClaudeCodeAdapter but got ${realAdapter::class.simpleName}. " +
                "Check SharedContextIntegFactory wiring."
        }

        // ── Test-scoped wiring ─────────────────────────────────────────────
        val sessionsState = SessionsState()
        val shepherdServer = ShepherdServer(sessionsState, outFactory)
        val clock = SystemClock()

        // Pick a random available port, then start the Ktor CIO server on it.
        // Note: TOCTOU race — the port is freed after ServerSocket closes, so another process
        // could theoretically claim it before embeddedServer binds. Unlikely in test environments.
        val serverPort = ServerSocket(0).use { it.localPort }
        val ktorServer = embeddedServer(CIO, port = serverPort) {
            shepherdServer.configureApplication(this)
        }.start(wait = false)

        // Resolve absolute path to callback script directory
        val scriptsDir = IntegTestHelpers.resolveCallbackScriptsDir()

        // Wrapping adapter that injects TICKET_SHEPHERD_SERVER_PORT and PATH into the command
        val wrappedAdapter = ServerPortInjectingAdapter(
            delegate = realAdapter,
            serverPort = serverPort,
            callbackScriptsDir = scriptsDir,
        )

        val sessionKiller = SingleSessionKiller { session -> sessionManager.killSession(session) }

        // Integration test timeouts: faster than production but generous enough for GLM
        val integTimeoutConfig = HarnessTimeoutConfig(
            healthTimeouts = HealthTimeoutLadder(
                startup = 3.minutes,
                normalActivity = 5.minutes,
                pingResponse = 1.minutes,
            ),
            healthCheckInterval = 5.seconds,
            payloadAckTimeout = 2.minutes,
            payloadAckMaxAttempts = 3,
        )

        val agentUnresponsiveUseCase = AgentUnresponsiveUseCaseImpl(outFactory, sessionKiller)
        val noOpQaDrainer = QaDrainer { _, _ -> }
        val stubContextWindowReader = ContextWindowStateReader { ContextWindowState(remainingPercentage = null) }

        val facade = AgentFacadeImpl(
            sessionsState = sessionsState,
            agentTypeAdapter = wrappedAdapter,
            tmuxSessionCreator = sessionManager,
            sessionKiller = sessionKiller,
            contextWindowStateReader = stubContextWindowReader,
            clock = clock,
            harnessTimeoutConfig = integTimeoutConfig,
            ackedPayloadSender = AckedPayloadSenderImpl(
                outFactory = outFactory,
                payloadCounter = AtomicInteger(1),
                ackTimeout = 2.minutes,
            ),
            agentUnresponsiveUseCase = agentUnresponsiveUseCase,
            qaDrainAndDeliverUseCase = noOpQaDrainer,
            outFactory = outFactory,
        )

        // Write a system prompt file for the agent
        val systemPromptFile = IntegTestHelpers.createSystemPromptFile()

        // Track spawned handles for cleanup
        val spawnedHandles = mutableListOf<SpawnedAgentHandle>()

        fun buildSpawnConfig(partName: String) = SpawnAgentConfig(
            partName = partName,
            subPartName = "doer",
            subPartIndex = 0,
            agentType = AgentType.CLAUDE_CODE,
            model = "sonnet",
            role = "DOER",
            systemPromptPath = systemPromptFile.toPath(),
            bootstrapMessage = IntegTestCallbackProtocol.BOOTSTRAP_MESSAGE,
        )

        afterEach {
            spawnedHandles.forEach { handle ->
                try {
                    facade.killSession(handle)
                } catch (_: Exception) {
                    // Session may already be killed
                }
            }
            spawnedHandles.clear()
        }

        afterSpec {
            ktorServer.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
            systemPromptFile.delete()
        }

        // ── Test: spawn agent → started signal ─────────────────────────────

        describe("WHEN spawnAgent is called with a valid config") {

            it("THEN the returned handle has a valid HandshakeGuid and resolved session ID") {
                val handle = facade.spawnAgent(buildSpawnConfig("integ-test"))
                spawnedHandles.add(handle)

                handle.guid.value.shouldNotBeEmpty()
                handle.sessionId.sessionId.shouldNotBeEmpty()
            }
        }

        // ── Test: spawn + send payload → done(COMPLETED) ──────────────────

        describe("WHEN a payload is sent to a spawned agent instructing it to signal done") {

            it("THEN sendPayloadAndAwaitSignal returns AgentSignal.Done(COMPLETED)") {
                val handle = facade.spawnAgent(buildSpawnConfig("integ-payload"))
                spawnedHandles.add(handle)

                // Create a temp instruction file telling the agent to signal done
                val instructionFile = IntegTestHelpers.createDoneInstructionFile()
                try {
                    val payload = AgentPayload(instructionFilePath = instructionFile.toPath())
                    val signal = facade.sendPayloadAndAwaitSignal(handle, payload)

                    signal.shouldBeInstanceOf<AgentSignal.Done>().result shouldBe DoneResult.COMPLETED
                } finally {
                    instructionFile.delete()
                }
            }
        }

        // ── Test: kill session ─────────────────────────────────────────────

        describe("WHEN killSession is called after spawn") {

            it("THEN the tmux session no longer exists") {
                val handle = facade.spawnAgent(buildSpawnConfig("integ-kill"))
                // DO NOT add to spawnedHandles -- we're killing it explicitly

                facade.killSession(handle)

                // Verify the session was removed from sessions state
                val entry = sessionsState.lookup(handle.guid)
                entry shouldBe null
            }
        }

        // ── Test: read context window state ────────────────────────────────

        describe("WHEN readContextWindowState is called after spawn") {

            it("THEN it returns a ContextWindowState with null remainingPercentage from stub") {
                val handle = facade.spawnAgent(buildSpawnConfig("integ-ctx"))
                spawnedHandles.add(handle)

                val state = facade.readContextWindowState(handle)

                // Stub reader returns null remainingPercentage — assert explicitly
                state.remainingPercentage shouldBe null
            }
        }
    }
})

// Uses shared ServerPortInjectingAdapter and IntegTestCallbackProtocol
// from the com.glassthought.shepherd.integtest package.
