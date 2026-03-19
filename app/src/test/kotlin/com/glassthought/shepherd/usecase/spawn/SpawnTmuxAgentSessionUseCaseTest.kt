package com.glassthought.shepherd.usecase.spawn

import com.asgard.core.out.OutFactory
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.tmux.SessionExistenceChecker
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.agent.tmux.TmuxSessionCreator
import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.data.HealthTimeoutLadder
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.state.SubPartStatus
import com.glassthought.shepherd.core.time.Clock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ── Test Fakes ──────────────────────────────────────────────────────────────

/** Records [buildStartCommand] calls and returns a fixed [TmuxStartCommand]. */
private class FakeAgentTypeAdapter(
    private val resolvedSessionId: String = "fake-session-id-123",
) : AgentTypeAdapter {

    val buildStartCommandCalls = mutableListOf<BuildStartCommandParams>()
    val resolveSessionIdCalls = mutableListOf<HandshakeGuid>()

    override fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand {
        buildStartCommandCalls.add(params)
        return TmuxStartCommand("fake-start-command")
    }

    override suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String {
        resolveSessionIdCalls.add(handshakeGuid)
        return resolvedSessionId
    }
}

/** Always succeeds; records created sessions for verification. */
private class FakeTmuxSessionCreator : TmuxSessionCreator {
    val createdSessions = mutableListOf<Pair<String, TmuxStartCommand>>()

    override suspend fun createSession(sessionName: String, startCommand: TmuxStartCommand): TmuxSession {
        createdSessions.add(sessionName to startCommand)
        return TmuxSession(
            name = TmuxSessionName(sessionName),
            paneTarget = "$sessionName:0.0",
            communicator = NoOpTmuxCommunicator,
            existsChecker = AlwaysExistsChecker,
        )
    }
}

/** Throws [IllegalStateException] on [createSession]. */
private class FailingTmuxSessionCreator(
    private val errorMessage: String = "tmux: server connection failed",
) : TmuxSessionCreator {
    override suspend fun createSession(sessionName: String, startCommand: TmuxStartCommand): TmuxSession {
        throw IllegalStateException(errorMessage)
    }
}

/** Fixed clock for deterministic timestamps in tests. */
private class FixedClock(private val fixedInstant: Instant = Instant.parse("2026-03-19T12:00:00Z")) : Clock {
    override fun now(): Instant = fixedInstant
}

// ── Minimal stubs ───────────────────────────────────────────────────────────

private object NoOpTmuxCommunicator : TmuxCommunicator {
    override suspend fun sendKeys(paneTarget: String, text: String) = Unit
    override suspend fun sendRawKeys(paneTarget: String, keys: String) = Unit
}

private object AlwaysExistsChecker : SessionExistenceChecker {
    override suspend fun exists(sessionName: TmuxSessionName): Boolean = true
}

// ── Constants ───────────────────────────────────────────────────────────────

private const val TEST_PART_NAME = "part_1"
private const val TEST_SUB_PART_NAME = "impl"
private const val TEST_MODEL = "sonnet"
private const val TEST_WORKING_DIR = "/tmp/test-workdir"
private const val TEST_BOOTSTRAP_MESSAGE = "Bootstrap: call started"
private const val TEST_SERVER_PORT = 8347
private const val TEST_RESOLVED_SESSION_ID = "resolved-session-abc"
private val TEST_FIXED_INSTANT = Instant.parse("2026-03-19T12:00:00Z")
private const val EXPECTED_SESSION_NAME = "shepherd_${TEST_PART_NAME}_${TEST_SUB_PART_NAME}"

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun createTestCurrentState(): CurrentState {
    return CurrentState(
        parts = mutableListOf(
            Part(
                name = TEST_PART_NAME,
                phase = Phase.EXECUTION,
                description = "Test part",
                subParts = listOf(
                    SubPart(
                        name = TEST_SUB_PART_NAME,
                        role = "DOER",
                        agentType = AgentType.CLAUDE_CODE.name,
                        model = TEST_MODEL,
                        status = SubPartStatus.IN_PROGRESS,
                    )
                ),
            )
        ),
    )
}

private fun createTestParams(
    startedDeferred: CompletableDeferred<Unit> = CompletableDeferred(),
): SpawnTmuxAgentSessionParams {
    return SpawnTmuxAgentSessionParams(
        partName = TEST_PART_NAME,
        subPartName = TEST_SUB_PART_NAME,
        agentType = AgentType.CLAUDE_CODE,
        model = TEST_MODEL,
        workingDir = TEST_WORKING_DIR,
        tools = listOf("Bash", "Read", "Write"),
        systemPromptFilePath = "/path/to/system-prompt.md",
        appendSystemPrompt = false,
        bootstrapMessage = TEST_BOOTSTRAP_MESSAGE,
        serverPort = TEST_SERVER_PORT,
        startedDeferred = startedDeferred,
    )
}

private fun createUseCase(
    adapter: AgentTypeAdapter = FakeAgentTypeAdapter(resolvedSessionId = TEST_RESOLVED_SESSION_ID),
    tmuxSessionCreator: TmuxSessionCreator = FakeTmuxSessionCreator(),
    healthTimeoutLadder: HealthTimeoutLadder = HealthTimeoutLadder(startup = 5.seconds),
    currentState: CurrentState = createTestCurrentState(),
    clock: Clock = FixedClock(TEST_FIXED_INSTANT),
    outFactory: OutFactory,
): SpawnTmuxAgentSessionUseCase {
    return SpawnTmuxAgentSessionUseCase(
        agentTypeAdapters = mapOf(AgentType.CLAUDE_CODE to adapter),
        tmuxSessionCreator = tmuxSessionCreator,
        healthTimeoutLadder = healthTimeoutLadder,
        currentState = currentState,
        clock = clock,
        outFactory = outFactory,
    )
}

// ── Tests ───────────────────────────────────────────────────────────────────

class SpawnTmuxAgentSessionUseCaseTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN happy path — startedDeferred completes immediately") {

            describe("WHEN execute is called") {

                it("THEN returns TmuxAgentSession with correct session name") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val useCase = createUseCase(outFactory = outFactory)

                    val result = useCase.execute(createTestParams(startedDeferred))

                    result.tmuxSession.name.sessionName shouldBe EXPECTED_SESSION_NAME
                }

                it("THEN returns TmuxAgentSession with correct pane target") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val useCase = createUseCase(outFactory = outFactory)

                    val result = useCase.execute(createTestParams(startedDeferred))

                    result.tmuxSession.paneTarget shouldBe "$EXPECTED_SESSION_NAME:0.0"
                }

                it("THEN returns resumableAgentSessionId with correct agentType") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val useCase = createUseCase(outFactory = outFactory)

                    val result = useCase.execute(createTestParams(startedDeferred))

                    result.resumableAgentSessionId.agentType shouldBe AgentType.CLAUDE_CODE
                }

                it("THEN returns resumableAgentSessionId with resolved sessionId") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val useCase = createUseCase(outFactory = outFactory)

                    val result = useCase.execute(createTestParams(startedDeferred))

                    result.resumableAgentSessionId.sessionId shouldBe TEST_RESOLVED_SESSION_ID
                }

                it("THEN returns resumableAgentSessionId with correct model") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val useCase = createUseCase(outFactory = outFactory)

                    val result = useCase.execute(createTestParams(startedDeferred))

                    result.resumableAgentSessionId.model shouldBe TEST_MODEL
                }

                it("THEN returns resumableAgentSessionId with handshakeGuid starting with 'handshake.'") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val useCase = createUseCase(outFactory = outFactory)

                    val result = useCase.execute(createTestParams(startedDeferred))

                    result.resumableAgentSessionId.handshakeGuid.value shouldStartWith "handshake."
                }
            }
        }

        describe("GIVEN happy path — session record storage") {

            describe("WHEN execute completes successfully") {

                it("THEN session record is added to CurrentState") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val currentState = createTestCurrentState()
                    val useCase = createUseCase(currentState = currentState, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    val subPart = currentState.parts[0].subParts[0]
                    subPart.sessionIds shouldNotBe null
                    subPart.sessionIds!!.size shouldBe 1
                }

                it("THEN session record has correct agentType") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val currentState = createTestCurrentState()
                    val useCase = createUseCase(currentState = currentState, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    val record = currentState.parts[0].subParts[0].sessionIds!!.first()
                    record.agentType shouldBe AgentType.CLAUDE_CODE.name
                }

                it("THEN session record has correct model") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val currentState = createTestCurrentState()
                    val useCase = createUseCase(currentState = currentState, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    val record = currentState.parts[0].subParts[0].sessionIds!!.first()
                    record.model shouldBe TEST_MODEL
                }

                it("THEN session record has correct timestamp from clock") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val currentState = createTestCurrentState()
                    val useCase = createUseCase(currentState = currentState, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    val record = currentState.parts[0].subParts[0].sessionIds!!.first()
                    record.timestamp shouldBe TEST_FIXED_INSTANT.toString()
                }

                it("THEN session record has resolved session ID in agentSession") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val currentState = createTestCurrentState()
                    val useCase = createUseCase(currentState = currentState, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    val record = currentState.parts[0].subParts[0].sessionIds!!.first()
                    record.agentSession.id shouldBe TEST_RESOLVED_SESSION_ID
                }

                it("THEN session record handshakeGuid starts with 'handshake.'") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val currentState = createTestCurrentState()
                    val useCase = createUseCase(currentState = currentState, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    val record = currentState.parts[0].subParts[0].sessionIds!!.first()
                    record.handshakeGuid shouldStartWith "handshake."
                }
            }
        }

        describe("GIVEN TmuxSessionCreator.createSession throws") {

            describe("WHEN execute is called") {

                it("THEN throws TmuxSessionCreationException") {
                    val params = createTestParams()
                    val useCase = createUseCase(
                        tmuxSessionCreator = FailingTmuxSessionCreator("tmux: server connection failed"),
                        outFactory = outFactory,
                    )

                    shouldThrow<TmuxSessionCreationException> {
                        useCase.execute(params)
                    }
                }

                it("THEN TmuxSessionCreationException contains the session name") {
                    val params = createTestParams()
                    val useCase = createUseCase(
                        tmuxSessionCreator = FailingTmuxSessionCreator("original error"),
                        outFactory = outFactory,
                    )

                    val exception = shouldThrow<TmuxSessionCreationException> {
                        useCase.execute(params)
                    }

                    exception.sessionName shouldBe EXPECTED_SESSION_NAME
                }

                it("THEN TmuxSessionCreationException wraps the original cause") {
                    val params = createTestParams()
                    val useCase = createUseCase(
                        tmuxSessionCreator = FailingTmuxSessionCreator("original error"),
                        outFactory = outFactory,
                    )

                    val exception = shouldThrow<TmuxSessionCreationException> {
                        useCase.execute(params)
                    }

                    exception.cause!!.message shouldContain "original error"
                }
            }
        }

        describe("GIVEN startedDeferred never completes (startup timeout)") {

            describe("WHEN execute is called") {

                it("THEN throws StartupTimeoutException") {
                    val neverCompletingDeferred = CompletableDeferred<Unit>()
                    val params = createTestParams(neverCompletingDeferred)
                    val useCase = createUseCase(
                        healthTimeoutLadder = HealthTimeoutLadder(startup = 100.milliseconds),
                        outFactory = outFactory,
                    )

                    shouldThrow<StartupTimeoutException> {
                        useCase.execute(params)
                    }
                }

                it("THEN StartupTimeoutException contains the session name") {
                    val neverCompletingDeferred = CompletableDeferred<Unit>()
                    val params = createTestParams(neverCompletingDeferred)
                    val useCase = createUseCase(
                        healthTimeoutLadder = HealthTimeoutLadder(startup = 100.milliseconds),
                        outFactory = outFactory,
                    )

                    val exception = shouldThrow<StartupTimeoutException> {
                        useCase.execute(params)
                    }

                    exception.sessionName shouldBe EXPECTED_SESSION_NAME
                }

                it("THEN StartupTimeoutException includes the timeout duration") {
                    val neverCompletingDeferred = CompletableDeferred<Unit>()
                    val params = createTestParams(neverCompletingDeferred)
                    val timeout = 200.milliseconds
                    val useCase = createUseCase(
                        healthTimeoutLadder = HealthTimeoutLadder(startup = timeout),
                        outFactory = outFactory,
                    )

                    val exception = shouldThrow<StartupTimeoutException> {
                        useCase.execute(params)
                    }

                    exception.timeout shouldBe timeout
                }
            }
        }

        describe("GIVEN TMUX session name format") {

            describe("WHEN execute is called") {

                it("THEN TMUX session is created with name 'shepherd_partName_subPartName'") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val fakeTmuxCreator = FakeTmuxSessionCreator()
                    val useCase = createUseCase(
                        tmuxSessionCreator = fakeTmuxCreator,
                        outFactory = outFactory,
                    )

                    useCase.execute(createTestParams(startedDeferred))

                    fakeTmuxCreator.createdSessions.size shouldBe 1
                    fakeTmuxCreator.createdSessions.first().first shouldBe EXPECTED_SESSION_NAME
                }
            }
        }

        describe("GIVEN BuildStartCommandParams construction") {

            describe("WHEN execute is called") {

                it("THEN passes correct bootstrapMessage to adapter") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val fakeAdapter = FakeAgentTypeAdapter(resolvedSessionId = TEST_RESOLVED_SESSION_ID)
                    val useCase = createUseCase(adapter = fakeAdapter, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    fakeAdapter.buildStartCommandCalls.first().bootstrapMessage shouldBe TEST_BOOTSTRAP_MESSAGE
                }

                it("THEN passes correct workingDir to adapter") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val fakeAdapter = FakeAgentTypeAdapter(resolvedSessionId = TEST_RESOLVED_SESSION_ID)
                    val useCase = createUseCase(adapter = fakeAdapter, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    fakeAdapter.buildStartCommandCalls.first().workingDir shouldBe TEST_WORKING_DIR
                }

                it("THEN passes correct model to adapter") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val fakeAdapter = FakeAgentTypeAdapter(resolvedSessionId = TEST_RESOLVED_SESSION_ID)
                    val useCase = createUseCase(adapter = fakeAdapter, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    fakeAdapter.buildStartCommandCalls.first().model shouldBe TEST_MODEL
                }

                it("THEN passes correct tools to adapter") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val fakeAdapter = FakeAgentTypeAdapter(resolvedSessionId = TEST_RESOLVED_SESSION_ID)
                    val useCase = createUseCase(adapter = fakeAdapter, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    fakeAdapter.buildStartCommandCalls.first().tools shouldBe listOf("Bash", "Read", "Write")
                }

                it("THEN passes correct systemPromptFilePath to adapter") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val fakeAdapter = FakeAgentTypeAdapter(resolvedSessionId = TEST_RESOLVED_SESSION_ID)
                    val useCase = createUseCase(adapter = fakeAdapter, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    fakeAdapter.buildStartCommandCalls.first().systemPromptFilePath shouldBe "/path/to/system-prompt.md"
                }

                it("THEN passes handshakeGuid starting with 'handshake.' to adapter") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    startedDeferred.complete(Unit)
                    val fakeAdapter = FakeAgentTypeAdapter(resolvedSessionId = TEST_RESOLVED_SESSION_ID)
                    val useCase = createUseCase(adapter = fakeAdapter, outFactory = outFactory)

                    useCase.execute(createTestParams(startedDeferred))

                    fakeAdapter.buildStartCommandCalls.first().handshakeGuid.value shouldStartWith "handshake."
                }
            }
        }

        describe("GIVEN no adapter registered for the agent type") {

            describe("WHEN execute is called with PI agent type") {

                it("THEN throws IllegalArgumentException mentioning PI") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    val params = createTestParams(startedDeferred).copy(agentType = AgentType.PI)
                    val useCase = createUseCase(outFactory = outFactory)

                    val exception = shouldThrow<IllegalArgumentException> {
                        useCase.execute(params)
                    }

                    exception.message shouldContain "PI"
                }
            }
        }

        describe("GIVEN startedDeferred completes asynchronously within timeout") {

            describe("WHEN execute is called") {

                it("THEN returns successfully") {
                    val startedDeferred = CompletableDeferred<Unit>()
                    val params = createTestParams(startedDeferred)
                    val useCase = createUseCase(
                        healthTimeoutLadder = HealthTimeoutLadder(startup = 5.seconds),
                        outFactory = outFactory,
                    )

                    // Complete the deferred asynchronously after a short delay
                    val job = launch {
                        kotlinx.coroutines.delay(50.milliseconds)
                        startedDeferred.complete(Unit)
                    }

                    val result = useCase.execute(params)

                    result.tmuxSession.name.sessionName shouldBe EXPECTED_SESSION_NAME
                    job.cancel()
                }
            }
        }
    },
)
