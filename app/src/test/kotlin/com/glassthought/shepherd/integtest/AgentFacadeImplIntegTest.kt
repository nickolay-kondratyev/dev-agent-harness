package com.glassthought.shepherd.integtest

import com.glassthought.bucket.isIntegTestEnabled
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.contextwindow.ContextWindowStateReader
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.facade.AgentFacadeImpl
import com.glassthought.shepherd.core.agent.facade.AgentPayload
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.ContextWindowState
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.SpawnAgentConfig
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
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
        val systemPromptFile = IntegTestHelpers.createIntegTestSystemPromptFile()

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
            bootstrapMessage = IntegTestHelpers.buildBootstrapMessage(),
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

// ── Helper utilities ────────────────────────────────────────────────────────

/**
 * Wrapping [AgentTypeAdapter] that injects `TICKET_SHEPHERD_SERVER_PORT` and
 * `callback_shepherd.signal.sh` PATH into the command built by the delegate.
 *
 * This enables the E2E test to provide the dynamically-assigned server port
 * and callback script location to the spawned agent's tmux session.
 *
 * COUPLING NOTE: This adapter assumes the delegate ([ClaudeCodeAdapter]) produces a command
 * in the format `bash -c '<inner command>'`. If ClaudeCodeAdapter changes its command format
 * (e.g., different quoting style or shell invocation), this injection will break. The `check`
 * call below guards against silent failures, but the string manipulation is inherently fragile.
 */
private class ServerPortInjectingAdapter(
    private val delegate: AgentTypeAdapter,
    private val serverPort: Int,
    private val callbackScriptsDir: String,
) : AgentTypeAdapter {

    override fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand {
        val delegateCommand = delegate.buildStartCommand(params)
        val originalCommand = delegateCommand.command

        // The delegate produces: bash -c '... inner command ...'
        // We inject env exports at the start of the inner command by replacing
        // the first occurrence of the inner command start.
        // Pattern: bash -c '<inner>' → bash -c 'export PORT=X && export PATH=... && <inner>'
        val envPrefix = "export TICKET_SHEPHERD_SERVER_PORT=$serverPort && " +
            "export PATH=\$PATH:$callbackScriptsDir && "

        // Find the position after "bash -c '" and insert the env prefix
        val bashCPrefix = "bash -c '"
        val insertionPoint = originalCommand.indexOf(bashCPrefix)
        check(insertionPoint >= 0) {
            "Expected command to contain '$bashCPrefix' but got: $originalCommand"
        }
        val afterPrefix = insertionPoint + bashCPrefix.length
        val modifiedCommand = originalCommand.substring(0, afterPrefix) +
            envPrefix +
            originalCommand.substring(afterPrefix)

        return TmuxStartCommand(modifiedCommand)
    }

    override suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String {
        return delegate.resolveSessionId(handshakeGuid)
    }
}

/**
 * Stateless helper utilities for [AgentFacadeImplIntegTest].
 */
private object IntegTestHelpers {

    /**
     * Resolves the absolute path to the callback scripts directory.
     * The scripts are at `app/src/main/resources/scripts/` relative to the project root.
     */
    fun resolveCallbackScriptsDir(): String {
        val projectDir = System.getProperty("user.dir")
        val scriptsDir = File(projectDir, "src/main/resources/scripts")
        check(scriptsDir.isDirectory) {
            "Callback scripts directory not found at ${scriptsDir.absolutePath}. " +
                "Ensure you are running from the app module directory."
        }
        // Ensure the script is executable
        val signalScript = File(scriptsDir, "callback_shepherd.signal.sh")
        check(signalScript.exists()) {
            "callback_shepherd.signal.sh not found at ${signalScript.absolutePath}"
        }
        if (!signalScript.canExecute()) {
            signalScript.setExecutable(true)
        }
        return scriptsDir.absolutePath
    }

    /**
     * Creates a temporary system prompt file that instructs the agent about the callback protocol.
     */
    fun createIntegTestSystemPromptFile(): File {
        val tmpDir = File(System.getProperty("user.dir"), ".tmp")
        tmpDir.mkdirs()
        val file = File(tmpDir, "integ-test-system-prompt-${System.currentTimeMillis()}.md")
        file.writeText(
            """
            |# Integration Test Agent Protocol
            |
            |You are a test agent running in an integration test. Follow these rules EXACTLY:
            |
            |## Callback Protocol
            |
            |You MUST use `callback_shepherd.signal.sh` (already on your PATH) to communicate with the harness.
            |
            |### On startup (FIRST thing you do):
            |```bash
            |callback_shepherd.signal.sh started
            |```
            |
            |### When you receive a payload wrapped in XML tags:
            |1. First ACK the payload:
            |```bash
            |callback_shepherd.signal.sh ack-payload <payload_id>
            |```
            |The payload_id is in the `payload_id` attribute of the XML tag.
            |
            |2. Then read and follow the instructions in the payload.
            |
            |### When done with work:
            |```bash
            |callback_shepherd.signal.sh done completed
            |```
            |
            |## IMPORTANT
            |- ALWAYS call `callback_shepherd.signal.sh started` FIRST before doing anything else.
            |- ALWAYS ACK payloads before processing them.
            |- ALWAYS signal done when you finish processing a payload's instructions.
            |- Use Bash tool to execute the callback scripts.
            """.trimMargin()
        )
        return file
    }

    /**
     * Builds the bootstrap message sent to the agent on spawn.
     * This message is the first thing the agent sees and must instruct it to call started.
     */
    fun buildBootstrapMessage(): String {
        return "You are a test agent. Your FIRST action must be to call " +
            "`callback_shepherd.signal.sh started` using the Bash tool. " +
            "This is critical — do it immediately before anything else. " +
            "After that, wait for further instructions via payload delivery."
    }

    /**
     * Creates a temporary instruction file that tells the agent to signal done.
     */
    fun createDoneInstructionFile(): File {
        val tmpDir = File(System.getProperty("user.dir"), ".tmp")
        tmpDir.mkdirs()
        val file = File(tmpDir, "integ-test-done-instruction-${System.currentTimeMillis()}.md")
        file.writeText(
            """
            |# Task: Signal Done
            |
            |Your task is simple: signal that you are done.
            |
            |Run this command using the Bash tool:
            |```bash
            |callback_shepherd.signal.sh done completed
            |```
            |
            |That is all you need to do.
            """.trimMargin()
        )
        return file
    }
}
