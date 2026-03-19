package com.glassthought.shepherd.integtest.compaction

import com.asgard.core.out.OutFactory
import com.glassthought.bucket.isIntegTestEnabled
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.contextwindow.ClaudeCodeContextWindowStateReader
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
import com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager
import com.glassthought.shepherd.core.compaction.SelfCompactionInstructionBuilder
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.data.HealthTimeoutLadder
import com.glassthought.shepherd.core.executor.PrivateMdValidator
import com.glassthought.shepherd.core.question.QaDrainer
import com.glassthought.shepherd.core.server.AckedPayloadSenderImpl
import com.glassthought.shepherd.core.server.ShepherdServer
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.time.SystemClock
import com.glassthought.shepherd.integtest.SharedContextDescribeSpec
import com.glassthought.shepherd.usecase.healthmonitoring.AgentUnresponsiveUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end integration test for the self-compaction flow.
 *
 * Validates:
 * 1. context_window_slim.json is readable after a real agent session starts
 * 2. Self-compaction can be triggered (agent writes PRIVATE.md, signals SelfCompacted)
 * 3. Session rotation produces a working new session with PRIVATE.md content
 *
 * Requires: tmux, `Z_AI_GLM_API_TOKEN`, `-PrunIntegTests=true`.
 */
@OptIn(ExperimentalKotest::class)
class SelfCompactionIntegTest : SharedContextDescribeSpec({

    describe("GIVEN self-compaction E2E flow with real agent")
        .config(isIntegTestEnabled()) {

        // ── Shared infrastructure from ShepherdContext ──────────────
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val realAdapter = shepherdContext.infra.claudeCode.agentTypeAdapter
        val outFactory = shepherdContext.infra.outFactory

        require(realAdapter is ClaudeCodeAdapter) {
            "Expected ClaudeCodeAdapter but got " +
                "${realAdapter::class.simpleName}. " +
                "Check SharedContextIntegFactory wiring."
        }

        // ── Test-scoped wiring ─────────────────────────────────────
        val sessionsState = SessionsState()
        val shepherdServer = ShepherdServer(sessionsState, outFactory)
        val clock = SystemClock()

        val serverPort = ServerSocket(0).use { it.localPort }
        val ktorServer = embeddedServer(CIO, port = serverPort) {
            shepherdServer.configureApplication(this)
        }.start(wait = false)

        val scriptsDir = CompactionIntegTestHelpers.resolveCallbackScriptsDir()

        val wrappedAdapter = CompactionServerPortInjectingAdapter(
            delegate = realAdapter,
            serverPort = serverPort,
            callbackScriptsDir = scriptsDir,
        )

        val sessionKiller = SingleSessionKiller { session ->
            sessionManager.killSession(session)
        }

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

        val agentUnresponsiveUseCase = AgentUnresponsiveUseCaseImpl(
            outFactory, sessionKiller,
        )
        val noOpQaDrainer = QaDrainer { _, _ -> }

        // Grouped dependencies for facade construction
        val facadeDeps = FacadeDeps(
            sessionsState = sessionsState,
            wrappedAdapter = wrappedAdapter,
            sessionManager = sessionManager,
            sessionKiller = sessionKiller,
            clock = clock,
            integTimeoutConfig = integTimeoutConfig,
            agentUnresponsiveUseCase = agentUnresponsiveUseCase,
            noOpQaDrainer = noOpQaDrainer,
            outFactory = outFactory,
        )

        // ── Temp directory for PRIVATE.md ──────────────────────────
        val tmpDir = File(
            System.getProperty("user.dir"),
            ".tmp/compaction-integ-test",
        )
        tmpDir.mkdirs()

        val systemPromptFile =
            CompactionIntegTestHelpers.createCompactionSystemPromptFile(tmpDir)

        // Track spawned handles and their facades for cleanup
        val spawnedEntries =
            mutableListOf<Pair<AgentFacadeImpl, SpawnedAgentHandle>>()

        afterEach {
            spawnedEntries.forEach { (facade, handle) ->
                try {
                    facade.killSession(handle)
                } catch (_: Exception) {
                    // Session may already be killed
                }
            }
            spawnedEntries.clear()
        }

        afterSpec {
            ktorServer.stop(
                gracePeriodMillis = 1000,
                timeoutMillis = 5000,
            )
            systemPromptFile.delete()
            tmpDir.listFiles()?.forEach { it.delete() }
        }

        // ── Scenario 1: context_window_slim.json readability ───────

        describe("WHEN agent starts and context_window_slim.json exists") {

            it("THEN ClaudeCodeContextWindowStateReader parses it") {
                val stubReader = ContextWindowStateReader {
                    ContextWindowState(remainingPercentage = null)
                }

                val facade = facadeDeps.buildFacade(stubReader)

                val handle = facade.spawnAgent(
                    CompactionIntegTestHelpers.buildSpawnConfig(
                        partName = "compaction-ctx-read",
                        systemPromptFile = systemPromptFile,
                    )
                )
                spawnedEntries.add(facade to handle)

                val agentSessionId = handle.sessionId.sessionId
                agentSessionId.shouldNotBeEmpty()

                // Write synthetic context_window_slim.json
                val sessionDir =
                    ClaudeCodeContextWindowStateReader.defaultBasePath()
                        .resolve(agentSessionId)
                Files.createDirectories(sessionDir)
                val ctxFile =
                    sessionDir.resolve("context_window_slim.json")
                Files.writeString(
                    ctxFile,
                    """
                    |{
                    |  "file_updated_timestamp": "${java.time.Instant.now()}",
                    |  "remaining_percentage": 35
                    |}
                    """.trimMargin()
                )

                try {
                    val reader = ClaudeCodeContextWindowStateReader(
                        clock = clock,
                        harnessTimeoutConfig = integTimeoutConfig,
                        outFactory = outFactory,
                    )

                    val state = reader.read(agentSessionId)
                    state.remainingPercentage.shouldNotBeNull()
                    state.remainingPercentage shouldBe 35
                } finally {
                    Files.deleteIfExists(ctxFile)
                    Files.deleteIfExists(sessionDir)
                }
            }
        }

        // ── Scenario 2: Self-compaction trigger ────────────────────

        describe("WHEN self-compaction instruction is sent to agent") {

            val privateMdPath = tmpDir.toPath()
                .resolve("PRIVATE-${System.currentTimeMillis()}.md")

            it("THEN agent signals SelfCompacted") {
                val stubReader = ContextWindowStateReader {
                    ContextWindowState(remainingPercentage = null)
                }

                val facade = facadeDeps.buildFacade(stubReader)

                val handle = facade.spawnAgent(
                    CompactionIntegTestHelpers.buildSpawnConfig(
                        partName = "compaction-trigger",
                        systemPromptFile = systemPromptFile,
                    )
                )
                spawnedEntries.add(facade to handle)

                val instructionText =
                    SelfCompactionInstructionBuilder().build(privateMdPath)
                val instructionFile =
                    CompactionIntegTestHelpers.createInstructionFile(
                        tmpDir = tmpDir,
                        name = "compaction-instruction",
                        content = instructionText,
                    )

                try {
                    val payload = AgentPayload(
                        instructionFilePath = instructionFile.toPath(),
                    )
                    val signal =
                        facade.sendPayloadAndAwaitSignal(handle, payload)
                    signal.shouldBeInstanceOf<AgentSignal.SelfCompacted>()
                } finally {
                    instructionFile.delete()
                }
            }

            it("THEN PRIVATE.md exists and is non-empty") {
                val validator = PrivateMdValidator()
                val result = validator.validate(
                    privateMdPath, "compaction-trigger",
                )
                result shouldBe PrivateMdValidator.ValidationResult.Valid
            }
        }

        // ── Scenario 3: Session rotation ───────────────────────────

        describe("WHEN session rotation is performed after compaction") {

            it("THEN new agent works with PRIVATE.md in prompt") {
                val stubReader = ContextWindowStateReader {
                    ContextWindowState(remainingPercentage = null)
                }

                val facade = facadeDeps.buildFacade(stubReader)

                // Step 1: Spawn initial agent
                val firstHandle = facade.spawnAgent(
                    CompactionIntegTestHelpers.buildSpawnConfig(
                        partName = "compaction-rotation",
                        systemPromptFile = systemPromptFile,
                    )
                )
                spawnedEntries.add(facade to firstHandle)

                // Step 2: Send compaction instruction
                val privateMdPath = tmpDir.toPath().resolve(
                    "PRIVATE-rotation-${System.currentTimeMillis()}.md",
                )
                val instructionText =
                    SelfCompactionInstructionBuilder().build(privateMdPath)
                val instructionFile =
                    CompactionIntegTestHelpers.createInstructionFile(
                        tmpDir = tmpDir,
                        name = "rotation-compaction",
                        content = instructionText,
                    )

                val compactionSignal: AgentSignal
                try {
                    val payload = AgentPayload(
                        instructionFilePath = instructionFile.toPath(),
                    )
                    compactionSignal = facade.sendPayloadAndAwaitSignal(
                        firstHandle, payload,
                    )
                } finally {
                    instructionFile.delete()
                }

                compactionSignal
                    .shouldBeInstanceOf<AgentSignal.SelfCompacted>()

                // Step 3: Kill old session
                facade.killSession(firstHandle)
                spawnedEntries.removeAll { it.second == firstHandle }

                // Step 4: New system prompt with PRIVATE.md content
                val privateMdContent = if (Files.exists(privateMdPath)) {
                    Files.readString(privateMdPath)
                } else {
                    "No PRIVATE.md — agent did not write the file."
                }
                val rotatedPrompt =
                    CompactionIntegTestHelpers.createRotatedSystemPromptFile(
                        tmpDir = tmpDir,
                        privateMdContent = privateMdContent,
                    )

                // Step 5: Spawn new agent with rotated system prompt
                val secondHandle = facade.spawnAgent(
                    CompactionIntegTestHelpers.buildSpawnConfig(
                        partName = "compaction-rotation-new",
                        systemPromptFile = rotatedPrompt,
                    )
                )
                spawnedEntries.add(facade to secondHandle)

                try {
                    // Step 6: Verify new session is functional
                    val doneFile =
                        CompactionIntegTestHelpers.createDoneInstructionFile(
                            tmpDir,
                        )
                    try {
                        val donePayload = AgentPayload(
                            instructionFilePath = doneFile.toPath(),
                        )
                        val doneSignal =
                            facade.sendPayloadAndAwaitSignal(
                                secondHandle, donePayload,
                            )
                        doneSignal.shouldBeInstanceOf<AgentSignal.Done>()
                            .result shouldBe DoneResult.COMPLETED
                    } finally {
                        doneFile.delete()
                    }
                } finally {
                    rotatedPrompt.delete()
                }
            }
        }
    }
})

// ── Facade dependency group ─────────────────────────────────────────────────

/**
 * Groups all dependencies needed to construct [AgentFacadeImpl] in tests,
 * avoiding long parameter lists on the factory method.
 */
private data class FacadeDeps(
    val sessionsState: SessionsState,
    val wrappedAdapter: AgentTypeAdapter,
    val sessionManager: TmuxSessionManager,
    val sessionKiller: SingleSessionKiller,
    val clock: SystemClock,
    val integTimeoutConfig: HarnessTimeoutConfig,
    val agentUnresponsiveUseCase: AgentUnresponsiveUseCaseImpl,
    val noOpQaDrainer: QaDrainer,
    val outFactory: OutFactory,
) {
    fun buildFacade(
        contextWindowStateReader: ContextWindowStateReader,
    ): AgentFacadeImpl {
        return AgentFacadeImpl(
            sessionsState = sessionsState,
            agentTypeAdapter = wrappedAdapter,
            tmuxSessionCreator = sessionManager,
            sessionKiller = sessionKiller,
            contextWindowStateReader = contextWindowStateReader,
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
    }
}

// ── Adapter that injects server port and PATH ───────────────────────────────

/**
 * Wrapping [AgentTypeAdapter] that injects `TICKET_SHEPHERD_SERVER_PORT`
 * and `callback_shepherd.signal.sh` PATH into the command.
 *
 * Duplicated from AgentFacadeImplIntegTest (private to that class).
 */
private class CompactionServerPortInjectingAdapter(
    private val delegate: AgentTypeAdapter,
    private val serverPort: Int,
    private val callbackScriptsDir: String,
) : AgentTypeAdapter {

    override fun buildStartCommand(
        params: BuildStartCommandParams,
    ): TmuxStartCommand {
        val delegateCommand = delegate.buildStartCommand(params)
        val originalCommand = delegateCommand.command

        val envPrefix =
            "export TICKET_SHEPHERD_SERVER_PORT=$serverPort && " +
                "export PATH=\$PATH:$callbackScriptsDir && "

        val bashCPrefix = "bash -c '"
        val insertionPoint = originalCommand.indexOf(bashCPrefix)
        check(insertionPoint >= 0) {
            "Expected command to contain '$bashCPrefix' " +
                "but got: $originalCommand"
        }
        val afterPrefix = insertionPoint + bashCPrefix.length
        val modifiedCommand =
            originalCommand.substring(0, afterPrefix) +
                envPrefix +
                originalCommand.substring(afterPrefix)

        return TmuxStartCommand(modifiedCommand)
    }

    override suspend fun resolveSessionId(
        handshakeGuid: HandshakeGuid,
    ): String {
        return delegate.resolveSessionId(handshakeGuid)
    }
}

// ── Helper utilities ────────────────────────────────────────────────────────

/**
 * Stateless helper utilities for [SelfCompactionIntegTest].
 */
private object CompactionIntegTestHelpers {

    fun resolveCallbackScriptsDir(): String {
        val projectDir = System.getProperty("user.dir")
        val scriptsDir = File(projectDir, "src/main/resources/scripts")
        check(scriptsDir.isDirectory) {
            "Callback scripts directory not found at " +
                "${scriptsDir.absolutePath}. " +
                "Ensure you are running from the app module directory."
        }
        val signalScript = File(
            scriptsDir, "callback_shepherd.signal.sh",
        )
        check(signalScript.exists()) {
            "callback_shepherd.signal.sh not found at " +
                signalScript.absolutePath
        }
        if (!signalScript.canExecute()) {
            signalScript.setExecutable(true)
        }
        return scriptsDir.absolutePath
    }

    fun buildSpawnConfig(
        partName: String,
        systemPromptFile: File,
    ) = SpawnAgentConfig(
        partName = partName,
        subPartName = "doer",
        subPartIndex = 0,
        agentType = AgentType.CLAUDE_CODE,
        model = "sonnet",
        role = "DOER",
        systemPromptPath = systemPromptFile.toPath(),
        bootstrapMessage = buildBootstrapMessage(),
    )

    private fun buildBootstrapMessage(): String {
        return "You are a test agent. Your FIRST action must be " +
            "to call `callback_shepherd.signal.sh started` using " +
            "the Bash tool. This is critical — do it immediately " +
            "before anything else. After that, wait for further " +
            "instructions via payload delivery."
    }

    @Suppress("MaxLineLength")
    fun createCompactionSystemPromptFile(tmpDir: File): File {
        val file = File(
            tmpDir,
            "compaction-integ-system-prompt-${System.currentTimeMillis()}.md",
        )
        file.writeText(
            """
            |# Integration Test Agent Protocol (Self-Compaction)
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
            |### When instructed to self-compact:
            |1. Write the summary file as instructed (PRIVATE.md at the specified path)
            |2. Then signal self-compaction:
            |```bash
            |callback_shepherd.signal.sh self-compacted
            |```
            |
            |### When done with work:
            |```bash
            |callback_shepherd.signal.sh done completed
            |```
            |
            |## IMPORTANT
            |- ALWAYS call `callback_shepherd.signal.sh started` FIRST before doing anything else.
            |- ALWAYS ACK payloads before processing them.
            |- When asked to self-compact: write the PRIVATE.md file FIRST, then signal `self-compacted`.
            |- When asked to signal done: signal `done completed`.
            |- Use Bash tool to execute the callback scripts.
            """.trimMargin()
        )
        return file
    }

    @Suppress("MaxLineLength")
    fun createRotatedSystemPromptFile(
        tmpDir: File,
        privateMdContent: String,
    ): File {
        val file = File(
            tmpDir,
            "rotated-system-prompt-${System.currentTimeMillis()}.md",
        )
        file.writeText(
            """
            |# Integration Test Agent Protocol (Rotated Session)
            |
            |You are a test agent running in an integration test after session rotation.
            |Follow these rules EXACTLY:
            |
            |## Previous Session Context (PRIVATE.md)
            |
            |The following is context from a previous session that was compacted:
            |
            |```
            |$privateMdContent
            |```
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

    fun createInstructionFile(
        tmpDir: File,
        name: String,
        content: String,
    ): File {
        val file = File(tmpDir, "$name-${System.currentTimeMillis()}.md")
        file.writeText(content)
        return file
    }

    fun createDoneInstructionFile(tmpDir: File): File {
        return createInstructionFile(
            tmpDir = tmpDir,
            name = "done-instruction",
            content = """
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
            """.trimMargin(),
        )
    }
}
