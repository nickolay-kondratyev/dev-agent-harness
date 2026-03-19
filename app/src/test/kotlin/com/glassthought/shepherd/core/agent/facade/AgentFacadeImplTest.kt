package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.out.impl.NoOpOutFactory
import com.asgard.core.out.OutFactory
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.contextwindow.ContextWindowStateReader
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.agent.tmux.SessionExistenceChecker
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.agent.tmux.TmuxSessionCreator
import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.data.HealthTimeoutLadder
import com.glassthought.shepherd.core.question.QaDrainAndDeliverUseCase
import com.glassthought.shepherd.core.question.UserQuestionContext
import com.glassthought.shepherd.core.server.AckedPayloadSender
import com.glassthought.shepherd.core.server.PayloadAckTimeoutException
import com.glassthought.shepherd.core.session.SessionEntry
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.time.TestClock
import com.glassthought.shepherd.usecase.healthmonitoring.AgentUnresponsiveUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.DetectionContext
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import com.glassthought.shepherd.usecase.healthmonitoring.UnresponsiveDiagnostics
import com.glassthought.shepherd.usecase.healthmonitoring.UnresponsiveHandleResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ── Test Fakes ─────────────────────────────────────────────────

private class FakeAdapterForFacade(
    private val resolvedSessionId: String = "fake-session-id-123",
) : AgentTypeAdapter {

    val buildCalls = mutableListOf<BuildStartCommandParams>()
    val resolveCalls = mutableListOf<HandshakeGuid>()

    override fun buildStartCommand(
        params: BuildStartCommandParams,
    ): TmuxStartCommand {
        buildCalls.add(params)
        return TmuxStartCommand("fake-start-command")
    }

    override suspend fun resolveSessionId(
        handshakeGuid: HandshakeGuid,
    ): String {
        resolveCalls.add(handshakeGuid)
        return resolvedSessionId
    }
}

private object NoOpCommunicator : TmuxCommunicator {
    override suspend fun sendKeys(paneTarget: String, text: String) = Unit
    override suspend fun sendRawKeys(paneTarget: String, keys: String) = Unit
}

private class FakeCreator(
    private val communicator: TmuxCommunicator = NoOpCommunicator,
    private val onSessionCreated: (suspend () -> Unit)? = null,
) : TmuxSessionCreator {
    val created = mutableListOf<String>()

    override suspend fun createSession(
        sessionName: String,
        startCommand: TmuxStartCommand,
    ): TmuxSession {
        created.add(sessionName)
        val session = TmuxSession(
            name = TmuxSessionName(sessionName),
            paneTarget = "$sessionName:0.0",
            communicator = communicator,
            existsChecker = SessionExistenceChecker { true },
        )
        onSessionCreated?.invoke()
        return session
    }
}

private class FakeKiller : SingleSessionKiller {
    val killed = mutableListOf<TmuxSession>()
    override suspend fun killSession(session: TmuxSession) {
        killed.add(session)
    }
}

private class FakeReader(
    private val result: ContextWindowState,
) : ContextWindowStateReader {
    val calls = mutableListOf<String>()
    override suspend fun read(agentSessionId: String): ContextWindowState {
        calls.add(agentSessionId)
        return result
    }
}

/**
 * Fake [AckedPayloadSender] that succeeds immediately by default.
 * Can be configured to throw [PayloadAckTimeoutException] to test crash path.
 */
private class FakeAckedPayloadSender(
    private val shouldThrow: Boolean = false,
) : AckedPayloadSender {
    val sendCalls = mutableListOf<String>()

    override suspend fun sendAndAwaitAck(
        tmuxSession: TmuxAgentSession,
        sessionEntry: SessionEntry,
        payloadContent: String,
    ) {
        sendCalls.add(payloadContent)
        if (shouldThrow) {
            throw PayloadAckTimeoutException("All attempts exhausted — fake")
        }
    }
}

/**
 * Fake [AgentUnresponsiveUseCase] that records calls and returns a configurable result.
 */
private class FakeAgentUnresponsiveUseCase(
    private val noActivityResult: UnresponsiveHandleResult = UnresponsiveHandleResult.PingSent,
    private val pingTimeoutResult: UnresponsiveHandleResult = UnresponsiveHandleResult.SessionKilled(
        AgentSignal.Crashed("fake ping timeout crash")
    ),
) : AgentUnresponsiveUseCase {
    data class HandleCall(
        val detectionContext: DetectionContext,
        val diagnostics: UnresponsiveDiagnostics,
    )

    val handleCalls = mutableListOf<HandleCall>()

    override suspend fun handle(
        detectionContext: DetectionContext,
        tmuxSession: TmuxSession,
        diagnostics: UnresponsiveDiagnostics,
    ): UnresponsiveHandleResult {
        handleCalls.add(HandleCall(detectionContext, diagnostics))
        return when (detectionContext) {
            DetectionContext.NO_ACTIVITY_TIMEOUT -> noActivityResult
            DetectionContext.PING_TIMEOUT -> pingTimeoutResult
            DetectionContext.STARTUP_TIMEOUT -> pingTimeoutResult
        }
    }
}

/**
 * Creates a [QaDrainAndDeliverUseCase] with fake dependencies that drain the queue
 * without doing real I/O. Tracks how many times [QaDrainAndDeliverUseCase.drainAndDeliver] was invoked.
 */
private class QaDrainTracker {
    var drainCallCount = 0

    /**
     * Builds a real [QaDrainAndDeliverUseCase] with fake deps.
     * The handler returns a canned answer, the writer returns a fake path,
     * and the sender is a no-op. The real `drainAndDeliver` logic runs and
     * drains the queue — which is what we need for tests.
     */
    fun buildUseCase(): QaDrainAndDeliverUseCase {
        val tracker = this
        return object : QaDrainAndDeliverUseCase(
            userQuestionHandler = { "fake answer" },
            qaAnswersFileWriter = { _, commInDir -> commInDir.resolve("qa_answers.md") },
            ackedPayloadSender = AckedPayloadSender { _, _, _ -> },
            outFactory = NoOpOutFactory(),
        ) {
            override suspend fun drainAndDeliver(sessionEntry: SessionEntry, commInDir: Path) {
                tracker.drainCallCount++
                // Drain the queue so isQAPending becomes false
                while (sessionEntry.questionQueue.poll() != null) { /* drain */ }
            }
        }
    }
}

// ── Constants ──────────────────────────────────────────────────

private const val PART = "part_1"
private const val SUB_PART = "impl"
private const val SUB_INDEX = 0
private const val MODEL = "sonnet"
private const val BOOTSTRAP = "Bootstrap: call started"
private const val RESOLVED_ID = "resolved-session-abc"
private val PROMPT_PATH: Path = Path.of("/path/to/system-prompt.md")
private val FIXED_INSTANT: Instant = Instant.parse("2026-03-19T12:00:00Z")
private const val EXPECTED_SESSION = "shepherd_${PART}_${SUB_PART}"

// ── Helpers ────────────────────────────────────────────────────

private fun config(): SpawnAgentConfig = SpawnAgentConfig(
    partName = PART,
    subPartName = SUB_PART,
    subPartIndex = SUB_INDEX,
    agentType = AgentType.CLAUDE_CODE,
    model = MODEL,
    role = "DOER",
    systemPromptPath = PROMPT_PATH,
    bootstrapMessage = BOOTSTRAP,
)

private data class Harness(
    val facade: AgentFacadeImpl,
    val sessions: SessionsState,
    val adapter: FakeAdapterForFacade,
    val creator: FakeCreator,
    val killer: FakeKiller,
    val reader: FakeReader,
    val clock: TestClock,
    val ackedPayloadSender: FakeAckedPayloadSender,
    val agentUnresponsiveUseCase: FakeAgentUnresponsiveUseCase,
    val qaDrainTracker: QaDrainTracker,
)

private fun harness(
    timeout: HarnessTimeoutConfig = HarnessTimeoutConfig.forTests(),
    ctxState: ContextWindowState = ContextWindowState(75),
    outFactory: OutFactory,
    communicator: TmuxCommunicator = NoOpCommunicator,
    onSessionCreated: (suspend () -> Unit)? = null,
    sessionsOverride: SessionsState? = null,
    ackedPayloadSenderOverride: FakeAckedPayloadSender? = null,
    agentUnresponsiveUseCaseOverride: FakeAgentUnresponsiveUseCase? = null,
): Harness {
    val sessions = sessionsOverride ?: SessionsState()
    val adapter = FakeAdapterForFacade(resolvedSessionId = RESOLVED_ID)
    val creator = FakeCreator(communicator = communicator, onSessionCreated = onSessionCreated)
    val killer = FakeKiller()
    val reader = FakeReader(result = ctxState)
    val clock = TestClock(FIXED_INSTANT)
    val ackedSender = ackedPayloadSenderOverride ?: FakeAckedPayloadSender()
    val unresponsiveUseCase = agentUnresponsiveUseCaseOverride ?: FakeAgentUnresponsiveUseCase()
    val qaDrainTracker = QaDrainTracker()

    val facade = AgentFacadeImpl(
        sessionsState = sessions,
        agentTypeAdapter = adapter,
        tmuxSessionCreator = creator,
        sessionKiller = killer,
        contextWindowStateReader = reader,
        clock = clock,
        harnessTimeoutConfig = timeout,
        ackedPayloadSender = ackedSender,
        agentUnresponsiveUseCase = unresponsiveUseCase,
        qaDrainAndDeliverUseCase = qaDrainTracker.buildUseCase(),
        outFactory = outFactory,
    )

    return Harness(
        facade, sessions, adapter, creator, killer,
        reader, clock, ackedSender, unresponsiveUseCase, qaDrainTracker,
    )
}

/**
 * Creates a harness that auto-completes the startup signal when the TMUX session
 * is created. Uses [FakeCreator.onSessionCreated] callback to complete the deferred
 * synchronously — no polling or delay needed.
 *
 * The callback removes entries via [SessionsState.removeAllForPart] to access the
 * placeholder deferred and completes it. This is safe because [AgentFacadeImpl.spawnAgent]
 * holds its own reference to the deferred and re-registers a real entry after startup.
 */
private fun autoCompletingHarness(
    ctxState: ContextWindowState = ContextWindowState(75),
    outFactory: OutFactory,
    partName: String = PART,
    ackedPayloadSenderOverride: FakeAckedPayloadSender? = null,
    agentUnresponsiveUseCaseOverride: FakeAgentUnresponsiveUseCase? = null,
): Harness {
    val sessions = SessionsState()
    return harness(
        ctxState = ctxState,
        outFactory = outFactory,
        onSessionCreated = {
            val removed = sessions.removeAllForPart(partName)
            for (entry in removed) {
                if (!entry.signalDeferred.isCompleted) {
                    entry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))
                }
            }
        },
        sessionsOverride = sessions,
        ackedPayloadSenderOverride = ackedPayloadSenderOverride,
        agentUnresponsiveUseCaseOverride = agentUnresponsiveUseCaseOverride,
    )
}

private fun makeHandle(sessionId: String = "nonexistent"): SpawnedAgentHandle {
    return SpawnedAgentHandle(
        guid = HandshakeGuid.generate(),
        sessionId = ResumableAgentSessionId(
            handshakeGuid = HandshakeGuid.generate(),
            agentType = AgentType.CLAUDE_CODE,
            sessionId = sessionId,
            model = MODEL,
        ),
        lastActivityTimestamp = FIXED_INSTANT,
    )
}

private fun fakeUserQuestionContext(): UserQuestionContext = UserQuestionContext(
    question = "What should I do?",
    partName = PART,
    subPartName = SUB_PART,
    subPartRole = SubPartRole.DOER,
    handshakeGuid = HandshakeGuid("handshake.test"),
)

// ── Tests ──────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class AgentFacadeImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        // ── spawnAgent ───────────────────────────────────────

        describe("GIVEN spawnAgent with startup completing") {
            describe("WHEN spawnAgent is called") {

                it("THEN calls adapter.buildStartCommand once") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    h.facade.spawnAgent(config())
                    h.adapter.buildCalls.size shouldBe 1
                }

                it("THEN passes correct bootstrapMessage") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    h.facade.spawnAgent(config())
                    h.adapter.buildCalls.first().bootstrapMessage shouldBe BOOTSTRAP
                }

                it("THEN passes correct model") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    h.facade.spawnAgent(config())
                    h.adapter.buildCalls.first().model shouldBe MODEL
                }

                it("THEN passes correct systemPromptFilePath") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    h.facade.spawnAgent(config())
                    val path = h.adapter.buildCalls.first().systemPromptFilePath
                    path shouldBe PROMPT_PATH.toString()
                }

                it("THEN passes handshakeGuid with prefix") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    h.facade.spawnAgent(config())
                    val guid = h.adapter.buildCalls.first().handshakeGuid
                    guid.value shouldStartWith "handshake."
                }

                it("THEN creates TMUX session with correct name") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    h.facade.spawnAgent(config())
                    h.creator.created.size shouldBe 1
                    h.creator.created.first() shouldBe EXPECTED_SESSION
                }

                it("THEN calls adapter.resolveSessionId") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    h.facade.spawnAgent(config())
                    h.adapter.resolveCalls.size shouldBe 1
                }

                it("THEN returns handle with guid prefix") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    val handle = h.facade.spawnAgent(config())
                    handle.guid.value shouldStartWith "handshake."
                }

                it("THEN returns handle with resolved sessionId") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    val handle = h.facade.spawnAgent(config())
                    handle.sessionId.sessionId shouldBe RESOLVED_ID
                }

                it("THEN returns handle with correct agentType") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    val handle = h.facade.spawnAgent(config())
                    handle.sessionId.agentType shouldBe AgentType.CLAUDE_CODE
                }

                it("THEN returns handle with timestamp from clock") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    val handle = h.facade.spawnAgent(config())
                    handle.lastActivityTimestamp shouldBe FIXED_INSTANT
                }

                it("THEN registers entry in SessionsState") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    val handle = h.facade.spawnAgent(config())
                    h.sessions.lookup(handle.guid) shouldNotBe null
                }
            }
        }

        describe("GIVEN spawnAgent startup timeout") {
            describe("WHEN startup signal never arrives") {

                it("THEN throws AgentSpawnException") {
                    val h = harness(
                        timeout = HarnessTimeoutConfig(
                            healthTimeouts = HealthTimeoutLadder(
                                startup = 100.milliseconds,
                            ),
                        ),
                        outFactory = outFactory,
                    )
                    shouldThrow<AgentSpawnException> {
                        h.facade.spawnAgent(config())
                    }
                }

                it("THEN cleans up SessionsState") {
                    val h = harness(
                        timeout = HarnessTimeoutConfig(
                            healthTimeouts = HealthTimeoutLadder(
                                startup = 100.milliseconds,
                            ),
                        ),
                        outFactory = outFactory,
                    )
                    try {
                        h.facade.spawnAgent(config())
                    } catch (_: AgentSpawnException) { /* expected */ }

                    h.sessions.removeAllForPart(PART).size shouldBe 0
                }

                it("THEN kills the TMUX session") {
                    val h = harness(
                        timeout = HarnessTimeoutConfig(
                            healthTimeouts = HealthTimeoutLadder(
                                startup = 100.milliseconds,
                            ),
                        ),
                        outFactory = outFactory,
                    )
                    try {
                        h.facade.spawnAgent(config())
                    } catch (_: AgentSpawnException) { /* expected */ }

                    h.killer.killed.size shouldBe 1
                }
            }
        }

        // ── killSession ──────────────────────────────────────

        describe("GIVEN killSession with existing entry") {
            describe("WHEN killSession is called") {

                it("THEN calls sessionKiller") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    val handle = h.facade.spawnAgent(config())
                    h.facade.killSession(handle)
                    h.killer.killed.size shouldBe 1
                }

                it("THEN removes entry from SessionsState") {
                    val h = autoCompletingHarness(outFactory = outFactory)
                    val handle = h.facade.spawnAgent(config())
                    h.facade.killSession(handle)
                    h.sessions.lookup(handle.guid) shouldBe null
                }
            }
        }

        describe("GIVEN killSession with non-existing entry") {
            describe("WHEN killSession is called") {

                it("THEN does not throw") {
                    val h = harness(outFactory = outFactory)
                    h.facade.killSession(makeHandle())
                }

                it("THEN does not call sessionKiller") {
                    val h = harness(outFactory = outFactory)
                    h.facade.killSession(makeHandle())
                    h.killer.killed.size shouldBe 0
                }
            }
        }

        // ── readContextWindowState ───────────────────────────

        describe("GIVEN readContextWindowState") {
            describe("WHEN called with a valid handle") {

                it("THEN delegates with correct sessionId") {
                    val h = harness(outFactory = outFactory)
                    h.facade.readContextWindowState(makeHandle("my-id"))
                    h.reader.calls.size shouldBe 1
                    h.reader.calls.first() shouldBe "my-id"
                }

                it("THEN returns the reader result") {
                    val expected = ContextWindowState(remainingPercentage = 42)
                    val h = harness(ctxState = expected, outFactory = outFactory)
                    val result = h.facade.readContextWindowState(makeHandle("x"))
                    result shouldBe expected
                }
            }
        }

        // ── sendPayloadAndAwaitSignal ─────────────────────────

        describe("GIVEN sendPayloadAndAwaitSignal") {

            describe("WHEN session entry does not exist") {

                it("THEN throws IllegalStateException") {
                    val h = harness(outFactory = outFactory)
                    val payload = AgentPayload(
                        instructionFilePath = Path.of("/tmp/instr.md"),
                    )
                    shouldThrow<IllegalStateException> {
                        h.facade.sendPayloadAndAwaitSignal(
                            makeHandle(), payload,
                        )
                    }
                }
            }

            describe("WHEN AckedPayloadSender throws PayloadAckTimeoutException") {

                it("THEN returns AgentSignal.Crashed") {
                    val h = autoCompletingHarness(
                        outFactory = outFactory,
                        ackedPayloadSenderOverride = FakeAckedPayloadSender(shouldThrow = true),
                    )
                    val handle = h.facade.spawnAgent(config())
                    val payload = AgentPayload(instructionFilePath = Path.of("/tmp/instr.md"))

                    val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)

                    (result is AgentSignal.Crashed) shouldBe true
                }

                it("THEN crash details mention payload ACK timeout") {
                    val h = autoCompletingHarness(
                        outFactory = outFactory,
                        ackedPayloadSenderOverride = FakeAckedPayloadSender(shouldThrow = true),
                    )
                    val handle = h.facade.spawnAgent(config())
                    val payload = AgentPayload(instructionFilePath = Path.of("/tmp/instr.md"))

                    val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)

                    (result as AgentSignal.Crashed).details shouldContain "Payload ACK timeout"
                }

                it("THEN kills the session") {
                    val h = autoCompletingHarness(
                        outFactory = outFactory,
                        ackedPayloadSenderOverride = FakeAckedPayloadSender(shouldThrow = true),
                    )
                    val handle = h.facade.spawnAgent(config())
                    val payload = AgentPayload(instructionFilePath = Path.of("/tmp/instr.md"))

                    h.facade.sendPayloadAndAwaitSignal(handle, payload)

                    h.killer.killed.size shouldBe 1
                }
            }

            describe("WHEN payload delivery succeeds") {

                it("THEN ackedPayloadSender.sendAndAwaitAck is called with instruction path") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(instructionFilePath = Path.of("/tmp/comm/in/instr.md"))

                        launch {
                            h.facade.sendPayloadAndAwaitSignal(handle, payload)
                        }

                        advanceTimeBy(500)
                        val entry = h.sessions.lookup(handle.guid)!!
                        entry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))
                        advanceTimeBy(1001)

                        h.ackedPayloadSender.sendCalls.size shouldBe 1
                        h.ackedPayloadSender.sendCalls.first() shouldBe "/tmp/comm/in/instr.md"
                    }
                }
            }

            describe("AND signal arrives before normalActivity timeout") {

                it("THEN returns the completed signal") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(instructionFilePath = Path.of("/tmp/comm/in/instr.md"))

                        val expected = AgentSignal.Done(DoneResult.COMPLETED)
                        val resultDeferred = CompletableDeferred<AgentSignal>()
                        launch {
                            val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)
                            resultDeferred.complete(result)
                        }

                        // Complete the signal deferred before the health check fires
                        advanceTimeBy(500)
                        val updatedEntry = h.sessions.lookup(handle.guid)!!
                        updatedEntry.signalDeferred.complete(expected)

                        advanceTimeBy(1001)
                        resultDeferred.await() shouldBe expected
                    }
                }

                it("THEN does not trigger any health checks") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(instructionFilePath = Path.of("/tmp/comm/in/instr.md"))

                        launch {
                            h.facade.sendPayloadAndAwaitSignal(handle, payload)
                        }

                        advanceTimeBy(500)
                        val updatedEntry = h.sessions.lookup(handle.guid)!!
                        updatedEntry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))

                        advanceTimeBy(1001)

                        h.agentUnresponsiveUseCase.handleCalls.size shouldBe 0
                    }
                }
            }

            describe("AND agent is active with fresh lastActivityTimestamp") {
                describe("WHEN health check fires") {

                    it("THEN no ping is sent") {
                        runTest {
                            val h = autoCompletingHarness(outFactory = outFactory)
                            val handle = h.facade.spawnAgent(config())
                            val payload = AgentPayload(instructionFilePath = Path.of("/tmp/comm/in/instr.md"))

                            launch {
                                h.facade.sendPayloadAndAwaitSignal(handle, payload)
                            }

                            // Advance coroutine time past healthCheckInterval (1s)
                            // but clock stays at FIXED_INSTANT so lastActivityTimestamp is fresh
                            advanceTimeBy(1001)

                            h.agentUnresponsiveUseCase.handleCalls.size shouldBe 0

                            // Complete the signal to end the loop
                            val updatedEntry = h.sessions.lookup(handle.guid)!!
                            updatedEntry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))
                            advanceTimeBy(1001)
                        }
                    }
                }
            }

            describe("AND lastActivityTimestamp is stale beyond normalActivity") {

                it("THEN sends a ping via AgentUnresponsiveUseCase with NO_ACTIVITY_TIMEOUT") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(
                            instructionFilePath = Path.of("/tmp/comm/in/instr.md"),
                        )

                        launch {
                            h.facade.sendPayloadAndAwaitSignal(handle, payload)
                        }

                        // Let method start, register entry, deliver payload
                        advanceTimeBy(1)

                        // NOW advance clock so lastActivityTimestamp becomes stale
                        h.clock.advance(6.seconds)
                        // Trigger health check (delay(1s) in loop)
                        advanceTimeBy(1000)

                        h.agentUnresponsiveUseCase.handleCalls.size shouldBe 1
                        val ctx = h.agentUnresponsiveUseCase.handleCalls.first()
                        ctx.detectionContext shouldBe DetectionContext.NO_ACTIVITY_TIMEOUT

                        // Simulate callback arriving during ping window
                        val entry = h.sessions.lookup(handle.guid)!!
                        entry.lastActivityTimestamp.set(h.clock.now())
                        advanceTimeBy(1001)

                        // Complete signal to end
                        entry.signalDeferred.complete(
                            AgentSignal.Done(DoneResult.COMPLETED),
                        )
                        advanceTimeBy(1001)
                    }
                }
            }

            describe("AND ping sent WHEN callback arrives during pingResponse window") {

                it("THEN agent is marked alive and loop continues") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(
                            instructionFilePath = Path.of("/tmp/comm/in/instr.md"),
                        )

                        val resultDeferred = CompletableDeferred<AgentSignal>()
                        launch {
                            val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)
                            resultDeferred.complete(result)
                        }

                        // Let method start and set lastActivityTimestamp
                        advanceTimeBy(1)
                        // Make stale to trigger ping
                        h.clock.advance(6.seconds)
                        advanceTimeBy(1000) // health check triggers NO_ACTIVITY_TIMEOUT

                        // Callback arrives during ping window
                        val entry = h.sessions.lookup(handle.guid)!!
                        entry.lastActivityTimestamp.set(h.clock.now())
                        advanceTimeBy(1001) // ping check → alive

                        val pingTimeoutCalls = h.agentUnresponsiveUseCase.handleCalls
                            .filter { it.detectionContext == DetectionContext.PING_TIMEOUT }
                        pingTimeoutCalls.size shouldBe 0

                        entry.signalDeferred.complete(
                            AgentSignal.Done(DoneResult.COMPLETED),
                        )
                        advanceTimeBy(1001)
                        resultDeferred.await() shouldBe AgentSignal.Done(DoneResult.COMPLETED)
                    }
                }
            }

            describe("AND ping sent WHEN no callback after pingResponse window") {

                it("THEN returns AgentSignal.Crashed") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(
                            instructionFilePath = Path.of("/tmp/comm/in/instr.md"),
                        )

                        val resultDeferred = CompletableDeferred<AgentSignal>()
                        launch {
                            val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)
                            resultDeferred.complete(result)
                        }

                        advanceTimeBy(1)
                        h.clock.advance(6.seconds)
                        advanceTimeBy(1000) // NO_ACTIVITY_TIMEOUT → PingSent

                        h.clock.advance(2.seconds)
                        advanceTimeBy(1001) // PING_TIMEOUT

                        val result = resultDeferred.await()
                        (result is AgentSignal.Crashed) shouldBe true
                    }
                }

                it("THEN AgentUnresponsiveUseCase is called with PING_TIMEOUT") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(
                            instructionFilePath = Path.of("/tmp/comm/in/instr.md"),
                        )

                        val resultDeferred = CompletableDeferred<AgentSignal>()
                        launch {
                            val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)
                            resultDeferred.complete(result)
                        }

                        advanceTimeBy(1)
                        h.clock.advance(6.seconds)
                        advanceTimeBy(1000)

                        h.clock.advance(2.seconds)
                        advanceTimeBy(1001)

                        resultDeferred.await()

                        val pingTimeoutCalls = h.agentUnresponsiveUseCase.handleCalls
                            .filter { it.detectionContext == DetectionContext.PING_TIMEOUT }
                        pingTimeoutCalls.size shouldBe 1
                    }
                }
            }

            describe("AND Q&A is pending WHEN normalActivity timeout would trigger") {

                it("THEN health checks are skipped and Q&A is drained") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(instructionFilePath = Path.of("/tmp/comm/in/instr.md"))

                        launch {
                            h.facade.sendPayloadAndAwaitSignal(handle, payload)
                        }

                        advanceTimeBy(500) // Let method start and register entry

                        // Add a question to the queue to make isQAPending true
                        val entry = h.sessions.lookup(handle.guid)!!
                        entry.questionQueue.add(fakeUserQuestionContext())

                        // Make clock stale beyond normalActivity
                        h.clock.advance(6.seconds)

                        advanceTimeBy(1001) // health check fires

                        // Q&A should have been drained instead of health check
                        h.qaDrainTracker.drainCallCount shouldBe 1
                        h.agentUnresponsiveUseCase.handleCalls.size shouldBe 0

                        // Complete signal to exit
                        entry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))
                        advanceTimeBy(1001)
                    }
                }
            }

            describe("AND Q&A completes WHEN normalActivity timeout reached") {

                it("THEN health checks resume normally") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(instructionFilePath = Path.of("/tmp/comm/in/instr.md"))

                        launch {
                            h.facade.sendPayloadAndAwaitSignal(handle, payload)
                        }

                        advanceTimeBy(500)

                        // Add Q&A
                        val entry = h.sessions.lookup(handle.guid)!!
                        entry.questionQueue.add(fakeUserQuestionContext())

                        h.clock.advance(6.seconds)
                        advanceTimeBy(1001) // Q&A drained

                        // Queue is now empty (drained by fake), but clock still stale
                        // Next health check should detect stale activity
                        h.clock.advance(6.seconds)
                        advanceTimeBy(1001) // health check

                        h.agentUnresponsiveUseCase.handleCalls.size shouldBe 1
                        val ctx = h.agentUnresponsiveUseCase.handleCalls.first()
                        ctx.detectionContext shouldBe DetectionContext.NO_ACTIVITY_TIMEOUT

                        // Alive during ping
                        entry.lastActivityTimestamp.set(h.clock.now())
                        advanceTimeBy(1001)

                        // Complete
                        entry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))
                        advanceTimeBy(1001)
                    }
                }
            }

            describe("WHEN payload delivery succeeds and signal completes with FailWorkflow") {

                it("THEN returns FailWorkflow signal") {
                    runTest {
                        val h = autoCompletingHarness(outFactory = outFactory)
                        val handle = h.facade.spawnAgent(config())
                        val payload = AgentPayload(instructionFilePath = Path.of("/tmp/comm/in/instr.md"))

                        val expected = AgentSignal.FailWorkflow("something went wrong")
                        val resultDeferred = CompletableDeferred<AgentSignal>()
                        launch {
                            val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)
                            resultDeferred.complete(result)
                        }

                        advanceTimeBy(500)
                        val entry = h.sessions.lookup(handle.guid)!!
                        entry.signalDeferred.complete(expected)

                        advanceTimeBy(1001)
                        resultDeferred.await() shouldBe expected
                    }
                }
            }
        }
    },
)
