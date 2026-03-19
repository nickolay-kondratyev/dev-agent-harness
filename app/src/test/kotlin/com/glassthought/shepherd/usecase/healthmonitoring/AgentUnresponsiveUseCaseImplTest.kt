package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.core.out.OutFactory
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.tmux.SessionExistenceChecker
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * Records kill calls for verification.
 */
internal class FakeSingleSessionKiller : SingleSessionKiller {
    val killedSessions = mutableListOf<TmuxSession>()

    override suspend fun killSession(session: TmuxSession) {
        killedSessions.add(session)
    }
}

/**
 * Recorded call to [TmuxCommunicator.sendKeys] or [TmuxCommunicator.sendRawKeys].
 */
internal data class SendKeysCall(val paneTarget: String, val keys: String)

/**
 * Records sendKeys and sendRawKeys calls for verification.
 */
internal class SpyTmuxCommunicator : TmuxCommunicator {
    val sentKeys = mutableListOf<SendKeysCall>()
    val sentRawKeys = mutableListOf<SendKeysCall>()

    override suspend fun sendKeys(paneTarget: String, text: String) {
        sentKeys.add(SendKeysCall(paneTarget, text))
    }

    override suspend fun sendRawKeys(paneTarget: String, keys: String) {
        sentRawKeys.add(SendKeysCall(paneTarget, keys))
    }
}

/**
 * Always returns true for existence checks.
 */
internal class AlwaysExistsChecker : SessionExistenceChecker {
    override suspend fun exists(sessionName: TmuxSessionName): Boolean = true
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private const val TEST_SESSION_NAME = "shepherd_test_agent"
private const val TEST_PANE_TARGET = "$TEST_SESSION_NAME:0.0"
private val TEST_HANDSHAKE_GUID = HandshakeGuid("test-guid-12345")
private val TEST_TIMEOUT_DURATION = 3.minutes
private val TEST_STALE_DURATION = 35.minutes

private fun createTestDiagnostics(
    handshakeGuid: HandshakeGuid = TEST_HANDSHAKE_GUID,
    timeoutDuration: kotlin.time.Duration = TEST_TIMEOUT_DURATION,
    staleDuration: kotlin.time.Duration = TEST_STALE_DURATION,
) = UnresponsiveDiagnostics(
    handshakeGuid = handshakeGuid,
    timeoutDuration = timeoutDuration,
    staleDuration = staleDuration,
)

private fun createTmuxSession(
    communicator: TmuxCommunicator = SpyTmuxCommunicator(),
    sessionName: String = TEST_SESSION_NAME,
) = TmuxSession(
    name = TmuxSessionName(sessionName),
    paneTarget = "$sessionName:0.0",
    communicator = communicator,
    existsChecker = AlwaysExistsChecker(),
)

private fun createUseCase(
    outFactory: OutFactory,
    sessionKiller: SingleSessionKiller = FakeSingleSessionKiller(),
) = AgentUnresponsiveUseCaseImpl(
    outFactory = outFactory,
    sessionKiller = sessionKiller,
)

// ── Tests ───────────────────────────────────────────────────────────────────

class AgentUnresponsiveUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN STARTUP_TIMEOUT detection context") {
            describe("WHEN handle is called") {
                it("THEN kills the TMUX session") {
                    val fakeKiller = FakeSingleSessionKiller()
                    val tmuxSession = createTmuxSession()
                    val useCase = createUseCase(outFactory, fakeKiller)

                    useCase.handle(DetectionContext.STARTUP_TIMEOUT, tmuxSession, createTestDiagnostics())

                    fakeKiller.killedSessions.size shouldBe 1
                }

                it("THEN kills the correct session") {
                    val fakeKiller = FakeSingleSessionKiller()
                    val tmuxSession = createTmuxSession()
                    val useCase = createUseCase(outFactory, fakeKiller)

                    useCase.handle(DetectionContext.STARTUP_TIMEOUT, tmuxSession, createTestDiagnostics())

                    fakeKiller.killedSessions.first().name.sessionName shouldBe TEST_SESSION_NAME
                }

                it("THEN returns SessionKilled result") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.STARTUP_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    result.shouldBeInstanceOf<UnresponsiveHandleResult.SessionKilled>()
                }

                it("THEN returns Crashed signal with STARTUP_TIMEOUT context in details") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.STARTUP_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    val signal = (result as UnresponsiveHandleResult.SessionKilled).signal
                    signal.details shouldContain "STARTUP_TIMEOUT"
                }

                it("THEN includes session name in Crashed signal details") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.STARTUP_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    val signal = (result as UnresponsiveHandleResult.SessionKilled).signal
                    signal.details shouldContain TEST_SESSION_NAME
                }

                it("THEN includes handshakeGuid in Crashed signal details") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.STARTUP_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    val signal = (result as UnresponsiveHandleResult.SessionKilled).signal
                    signal.details shouldContain TEST_HANDSHAKE_GUID.value
                }
            }
        }

        describe("GIVEN NO_ACTIVITY_TIMEOUT detection context") {
            describe("WHEN handle is called") {
                it("THEN does NOT kill the session") {
                    val fakeKiller = FakeSingleSessionKiller()
                    val useCase = createUseCase(outFactory, fakeKiller)
                    val tmuxSession = createTmuxSession()

                    useCase.handle(DetectionContext.NO_ACTIVITY_TIMEOUT, tmuxSession, createTestDiagnostics())

                    fakeKiller.killedSessions.size shouldBe 0
                }

                it("THEN sends a health ping via sendRawKeys") {
                    val spyCommunicator = SpyTmuxCommunicator()
                    val tmuxSession = createTmuxSession(communicator = spyCommunicator)
                    val useCase = createUseCase(outFactory)

                    useCase.handle(DetectionContext.NO_ACTIVITY_TIMEOUT, tmuxSession, createTestDiagnostics())

                    spyCommunicator.sentRawKeys.size shouldBe 1
                }

                it("THEN sends ping to the correct pane target") {
                    val spyCommunicator = SpyTmuxCommunicator()
                    val tmuxSession = createTmuxSession(communicator = spyCommunicator)
                    val useCase = createUseCase(outFactory)

                    useCase.handle(DetectionContext.NO_ACTIVITY_TIMEOUT, tmuxSession, createTestDiagnostics())

                    spyCommunicator.sentRawKeys.first().paneTarget shouldBe TEST_PANE_TARGET
                }

                it("THEN returns PingSent result") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.NO_ACTIVITY_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    result.shouldBeInstanceOf<UnresponsiveHandleResult.PingSent>()
                }
            }
        }

        describe("GIVEN PING_TIMEOUT detection context") {
            describe("WHEN handle is called") {
                it("THEN kills the TMUX session") {
                    val fakeKiller = FakeSingleSessionKiller()
                    val tmuxSession = createTmuxSession()
                    val useCase = createUseCase(outFactory, fakeKiller)

                    useCase.handle(DetectionContext.PING_TIMEOUT, tmuxSession, createTestDiagnostics())

                    fakeKiller.killedSessions.size shouldBe 1
                }

                it("THEN returns SessionKilled result") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.PING_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    result.shouldBeInstanceOf<UnresponsiveHandleResult.SessionKilled>()
                }

                it("THEN returns Crashed signal with PING_TIMEOUT context in details") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.PING_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    val signal = (result as UnresponsiveHandleResult.SessionKilled).signal
                    signal.details shouldContain "PING_TIMEOUT"
                }

                it("THEN includes session name in Crashed signal details") {
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()

                    val result = useCase.handle(
                        DetectionContext.PING_TIMEOUT, tmuxSession, createTestDiagnostics()
                    )

                    val signal = (result as UnresponsiveHandleResult.SessionKilled).signal
                    signal.details shouldContain TEST_SESSION_NAME
                }
            }
        }

        describe("GIVEN session killer throws an exception") {
            describe("WHEN handle is called with STARTUP_TIMEOUT") {
                it("THEN the exception bubbles up") {
                    val throwingKiller = SingleSessionKiller { _ ->
                        error("tmux not found")
                    }
                    val useCase = AgentUnresponsiveUseCaseImpl(
                        outFactory = outFactory,
                        sessionKiller = throwingKiller,
                    )
                    val tmuxSession = createTmuxSession()

                    val exception = io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                        useCase.handle(DetectionContext.STARTUP_TIMEOUT, tmuxSession, createTestDiagnostics())
                    }

                    exception.message shouldContain "tmux not found"
                }
            }
        }

        describe("GIVEN different diagnostic values") {
            describe("WHEN handle is called with STARTUP_TIMEOUT") {
                it("THEN timeout duration is included in Crashed signal details") {
                    val customTimeout = 5.seconds
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()
                    val diagnostics = createTestDiagnostics(timeoutDuration = customTimeout)

                    val result = useCase.handle(DetectionContext.STARTUP_TIMEOUT, tmuxSession, diagnostics)

                    val signal = (result as UnresponsiveHandleResult.SessionKilled).signal
                    signal.details shouldContain customTimeout.toString()
                }
            }

            describe("WHEN handle is called with PING_TIMEOUT") {
                it("THEN stale duration is included in Crashed signal details") {
                    val customStale = 45.minutes
                    val useCase = createUseCase(outFactory)
                    val tmuxSession = createTmuxSession()
                    val diagnostics = createTestDiagnostics(staleDuration = customStale)

                    val result = useCase.handle(DetectionContext.PING_TIMEOUT, tmuxSession, diagnostics)

                    val signal = (result as UnresponsiveHandleResult.SessionKilled).signal
                    signal.details shouldContain customStale.toString()
                }
            }
        }
    },
)
