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
import kotlinx.coroutines.launch
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

private class FakeCreator : TmuxSessionCreator {
    val created = mutableListOf<String>()

    override suspend fun createSession(
        sessionName: String,
        startCommand: TmuxStartCommand,
    ): TmuxSession {
        created.add(sessionName)
        return TmuxSession(
            name = TmuxSessionName(sessionName),
            paneTarget = "$sessionName:0.0",
            communicator = NoOpCommunicator,
            existsChecker = SessionExistenceChecker { true },
        )
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
): Harness {
    val sessions = SessionsState()
    val adapter = FakeAdapterForFacade(resolvedSessionId = RESOLVED_ID)
    val creator = FakeCreator()
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
 * Spawns an agent, auto-completing the startup signal.
 *
 * Polls [SessionsState] by removing entries for [PART] and completing
 * the signal deferred. This is a pragmatic workaround because
 * [SessionsState] doesn't expose an "iterate all entries" method.
 */
private suspend fun kotlinx.coroutines.CoroutineScope.spawn(
    h: Harness,
    cfg: SpawnAgentConfig = config(),
): SpawnedAgentHandle {
    val job = launch {
        while (true) {
            val removed = h.sessions.removeAllForPart(cfg.partName)
            for (entry in removed) {
                if (!entry.signalDeferred.isCompleted) {
                    entry.signalDeferred.complete(
                        AgentSignal.Done(DoneResult.COMPLETED),
                    )
                }
            }
            kotlinx.coroutines.delay(5.milliseconds)
        }
    }
    val handle = h.facade.spawnAgent(cfg)
    job.cancel()
    return handle
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
                    val h = harness(outFactory = outFactory)
                    spawn(h)
                    h.adapter.buildCalls.size shouldBe 1
                }

                it("THEN passes correct bootstrapMessage") {
                    val h = harness(outFactory = outFactory)
                    spawn(h)
                    h.adapter.buildCalls.first().bootstrapMessage shouldBe BOOTSTRAP
                }

                it("THEN passes correct model") {
                    val h = harness(outFactory = outFactory)
                    spawn(h)
                    h.adapter.buildCalls.first().model shouldBe MODEL
                }

                it("THEN passes correct systemPromptFilePath") {
                    val h = harness(outFactory = outFactory)
                    spawn(h)
                    val path = h.adapter.buildCalls.first().systemPromptFilePath
                    path shouldBe PROMPT_PATH.toString()
                }

                it("THEN passes handshakeGuid with prefix") {
                    val h = harness(outFactory = outFactory)
                    spawn(h)
                    val guid = h.adapter.buildCalls.first().handshakeGuid
                    guid.value shouldStartWith "handshake."
                }

                it("THEN creates TMUX session with correct name") {
                    val h = harness(outFactory = outFactory)
                    spawn(h)
                    h.creator.created.size shouldBe 1
                    h.creator.created.first() shouldBe EXPECTED_SESSION
                }

                it("THEN calls adapter.resolveSessionId") {
                    val h = harness(outFactory = outFactory)
                    spawn(h)
                    h.adapter.resolveCalls.size shouldBe 1
                }

                it("THEN returns handle with guid prefix") {
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
                    handle.guid.value shouldStartWith "handshake."
                }

                it("THEN returns handle with resolved sessionId") {
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
                    handle.sessionId.sessionId shouldBe RESOLVED_ID
                }

                it("THEN returns handle with correct agentType") {
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
                    handle.sessionId.agentType shouldBe AgentType.CLAUDE_CODE
                }

                it("THEN returns handle with timestamp from clock") {
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
                    handle.lastActivityTimestamp shouldBe FIXED_INSTANT
                }

                it("THEN registers entry in SessionsState") {
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
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
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
                    h.facade.killSession(handle)
                    h.killer.killed.size shouldBe 1
                }

                it("THEN removes entry from SessionsState") {
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
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
                    val h = harness(outFactory = outFactory)
                    val handle = spawn(h)
                    val expected = AgentSignal.Done(DoneResult.COMPLETED)

                    val job = launch {
                        kotlinx.coroutines.delay(50.milliseconds)
                        val entry = h.sessions.lookup(handle.guid)!!
                        entry.signalDeferred.complete(expected)
                    }

                    val payload = AgentPayload(
                        instructionFilePath = Path.of("/tmp/instr.md"),
                    )
                    val result = h.facade.sendPayloadAndAwaitSignal(
                        handle, payload,
                    )
                    result shouldBe expected
                    job.cancel()
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
