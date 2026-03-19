package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.out.OutFactory
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
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
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.time.TestClock
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

// ── Test Fakes ──────────────────────────────────────────────────────

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

/**
 * Communicator that completes the session's signal deferred when [sendKeys] is called.
 * Used by the sendPayloadAndAwaitSignal test to simulate an agent responding to a payload
 * without delay-based synchronization.
 *
 * Looks up the entry in [sessions] by [partName], completes the first incomplete deferred,
 * and re-registers the entry so the lookup remains valid.
 */
private class SignalCompletingCommunicator(
    private val sessions: SessionsState,
    private val partName: String,
    private val signal: AgentSignal,
) : TmuxCommunicator {
    override suspend fun sendKeys(paneTarget: String, text: String) {
        val removed = sessions.removeAllForPart(partName)
        for (entry in removed) {
            if (!entry.signalDeferred.isCompleted) {
                entry.signalDeferred.complete(signal)
            }
        }
    }

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

// ── Constants ───────────────────────────────────────────────────────

private const val PART = "part_1"
private const val SUB_PART = "impl"
private const val SUB_INDEX = 0
private const val MODEL = "sonnet"
private const val BOOTSTRAP = "Bootstrap: call started"
private const val RESOLVED_ID = "resolved-session-abc"
private val PROMPT_PATH: Path = Path.of("/path/to/system-prompt.md")
private val FIXED_INSTANT: Instant = Instant.parse("2026-03-19T12:00:00Z")
private const val EXPECTED_SESSION = "shepherd_${PART}_${SUB_PART}"

// ── Helpers ─────────────────────────────────────────────────────────

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
)

private fun harness(
    timeout: HarnessTimeoutConfig = HarnessTimeoutConfig.forTests(),
    ctxState: ContextWindowState = ContextWindowState(75),
    outFactory: OutFactory,
    communicator: TmuxCommunicator = NoOpCommunicator,
    onSessionCreated: (suspend () -> Unit)? = null,
    sessionsOverride: SessionsState? = null,
): Harness {
    val sessions = sessionsOverride ?: SessionsState()
    val adapter = FakeAdapterForFacade(resolvedSessionId = RESOLVED_ID)
    val creator = FakeCreator(communicator = communicator, onSessionCreated = onSessionCreated)
    val killer = FakeKiller()
    val reader = FakeReader(result = ctxState)
    val clock = TestClock(FIXED_INSTANT)

    val facade = AgentFacadeImpl(
        sessionsState = sessions,
        agentTypeAdapter = adapter,
        tmuxSessionCreator = creator,
        sessionKiller = killer,
        contextWindowStateReader = reader,
        clock = clock,
        harnessTimeoutConfig = timeout,
        outFactory = outFactory,
    )

    return Harness(facade, sessions, adapter, creator, killer, reader, clock)
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

// ── Tests ───────────────────────────────────────────────────────────

class AgentFacadeImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        // ── spawnAgent ──────────────────────────────────────────────

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

        // ── killSession ─────────────────────────────────────────────

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

        // ── readContextWindowState ──────────────────────────────────

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

        // ── sendPayloadAndAwaitSignal (V1 stub) ────────────────────

        describe("GIVEN sendPayloadAndAwaitSignal") {
            describe("WHEN signal deferred is completed") {

                it("THEN returns the completed signal") {
                    val expected = AgentSignal.Done(DoneResult.COMPLETED)
                    val sessions = SessionsState()
                    val communicator = SignalCompletingCommunicator(
                        sessions = sessions,
                        partName = PART,
                        signal = expected,
                    )
                    val h = harness(
                        outFactory = outFactory,
                        communicator = communicator,
                        sessionsOverride = sessions,
                        onSessionCreated = {
                            val removed = sessions.removeAllForPart(PART)
                            for (entry in removed) {
                                if (!entry.signalDeferred.isCompleted) {
                                    entry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))
                                }
                            }
                        },
                    )
                    val handle = h.facade.spawnAgent(config())

                    val payload = AgentPayload(
                        instructionFilePath = Path.of("/tmp/instr.md"),
                    )
                    val result = h.facade.sendPayloadAndAwaitSignal(handle, payload)
                    result shouldBe expected
                }
            }

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
        }
    },
)
