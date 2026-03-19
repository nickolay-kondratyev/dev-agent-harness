package com.glassthought.shepherd.core.agent.facade

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.infra.DispatcherProvider
import com.glassthought.shepherd.core.time.TestClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAgentFacadeTest : AsgardDescribeSpec({

    // ── Helpers ────────────────────────────────────────────────────────

    fun buildHandle(
        guidSuffix: String = "test-guid",
        sessionId: String = "session-1",
        model: String = "test-model",
        timestamp: Instant = Instant.EPOCH,
    ): SpawnedAgentHandle {
        val guid = HandshakeGuid("handshake.$guidSuffix")
        return SpawnedAgentHandle(
            guid = guid,
            sessionId = ResumableAgentSessionId(
                handshakeGuid = guid,
                agentType = AgentType.CLAUDE_CODE,
                sessionId = sessionId,
                model = model,
            ),
            lastActivityTimestamp = timestamp,
        )
    }

    fun buildConfig(partName: String = "part_1"): SpawnAgentConfig =
        SpawnAgentConfig(
            partName = partName,
            subPartName = "doer",
            subPartIndex = 0,
            agentType = AgentType.CLAUDE_CODE,
            model = "test-model",
            role = "DOER",
            systemPromptPath = Path.of("/tmp/prompt.md"),
            bootstrapMessage = "bootstrap",
        )

    fun buildPayload(path: String = "/tmp/instructions.md"): AgentPayload =
        AgentPayload(instructionFilePath = Path.of(path))

    // ── Tests: spawnAgent ──────────────────────────────────────────────

    describe("GIVEN a FakeAgentFacade with programmed spawn behavior") {
        describe("WHEN spawnAgent is called") {

            it("THEN it returns the pre-programmed handle") {
                val facade = FakeAgentFacade()
                val expectedHandle = buildHandle()
                facade.onSpawn { expectedHandle }

                val result = facade.spawnAgent(buildConfig())
                result shouldBe expectedHandle
            }

            it("THEN the spawn call is recorded") {
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle() }

                facade.spawnAgent(buildConfig())
                facade.spawnCalls shouldHaveSize 1
            }

            it("THEN the recorded config matches what was passed") {
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle() }

                facade.spawnAgent(buildConfig("part_1"))
                facade.spawnCalls.first().partName shouldBe "part_1"
            }
        }
    }

    describe("GIVEN a FakeAgentFacade with NO spawn behavior programmed") {
        describe("WHEN spawnAgent is called") {
            it("THEN it throws IllegalStateException (fail hard)") {
                val facade = FakeAgentFacade()
                shouldThrow<IllegalStateException> {
                    facade.spawnAgent(buildConfig())
                }
            }
        }
    }

    // ── Tests: sendPayloadAndAwaitSignal ───────────────────────────────

    describe("GIVEN a FakeAgentFacade programmed to return Done(COMPLETED)") {
        describe("WHEN sendPayloadAndAwaitSignal is called") {

            it("THEN it returns Done(COMPLETED)") {
                val facade = FakeAgentFacade()
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.COMPLETED)
                }

                val signal = facade.sendPayloadAndAwaitSignal(
                    buildHandle(), buildPayload()
                )
                signal shouldBe AgentSignal.Done(DoneResult.COMPLETED)
            }

            it("THEN the call is recorded with handle and payload") {
                val facade = FakeAgentFacade()
                val handle = buildHandle()
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.COMPLETED)
                }

                facade.sendPayloadAndAwaitSignal(handle, buildPayload())
                facade.sendPayloadCalls.last().handle shouldBe handle
            }
        }
    }

    describe("GIVEN a FakeAgentFacade programmed with sequential signals") {
        describe("WHEN sendPayloadAndAwaitSignal is called twice") {

            it("THEN the first call returns NEEDS_ITERATION") {
                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION),
                        AgentSignal.Done(DoneResult.COMPLETED),
                    )
                )
                val facade = FakeAgentFacade()
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    signalQueue.removeFirst()
                }

                val first = facade.sendPayloadAndAwaitSignal(
                    buildHandle(), buildPayload()
                )
                first shouldBe AgentSignal.Done(DoneResult.NEEDS_ITERATION)
            }

            it("THEN the second call returns COMPLETED") {
                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION),
                        AgentSignal.Done(DoneResult.COMPLETED),
                    )
                )
                val facade = FakeAgentFacade()
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    signalQueue.removeFirst()
                }
                val handle = buildHandle()

                facade.sendPayloadAndAwaitSignal(handle, buildPayload())
                val second = facade.sendPayloadAndAwaitSignal(
                    handle, buildPayload()
                )
                second shouldBe AgentSignal.Done(DoneResult.COMPLETED)
            }
        }
    }

    describe("GIVEN a FakeAgentFacade with NO sendPayloadAndAwaitSignal programmed") {
        describe("WHEN sendPayloadAndAwaitSignal is called") {
            it("THEN it throws IllegalStateException (fail hard)") {
                val facade = FakeAgentFacade()
                shouldThrow<IllegalStateException> {
                    facade.sendPayloadAndAwaitSignal(
                        buildHandle(), buildPayload()
                    )
                }
            }
        }
    }

    // ── Tests: readContextWindowState ──────────────────────────────────

    describe("GIVEN a FakeAgentFacade programmed with 75% remaining context") {
        describe("WHEN readContextWindowState is called") {

            it("THEN it returns 75% remaining") {
                val facade = FakeAgentFacade()
                facade.onReadContextWindowState {
                    ContextWindowState(remainingPercentage = 75)
                }

                val state = facade.readContextWindowState(buildHandle())
                state.remainingPercentage shouldBe 75
            }

            it("THEN the call is recorded") {
                val facade = FakeAgentFacade()
                facade.onReadContextWindowState {
                    ContextWindowState(remainingPercentage = 75)
                }

                facade.readContextWindowState(buildHandle())
                facade.readContextWindowStateCalls shouldHaveSize 1
            }
        }
    }

    describe("GIVEN a FakeAgentFacade programmed with null remaining (stale)") {
        describe("WHEN readContextWindowState is called") {
            it("THEN remainingPercentage is null") {
                val facade = FakeAgentFacade()
                facade.onReadContextWindowState {
                    ContextWindowState(remainingPercentage = null)
                }

                val state = facade.readContextWindowState(buildHandle())
                state.remainingPercentage shouldBe null
            }
        }
    }

    // ── Tests: readContextWindowState not programmed ───────────────────

    describe("GIVEN a FakeAgentFacade with NO readContextWindowState programmed") {
        describe("WHEN readContextWindowState is called") {
            it("THEN it throws IllegalStateException (fail hard)") {
                val facade = FakeAgentFacade()
                shouldThrow<IllegalStateException> {
                    facade.readContextWindowState(buildHandle())
                }
            }
        }
    }

    // ── Tests: killSession ─────────────────────────────────────────────

    describe("GIVEN a FakeAgentFacade (default kill behavior)") {
        describe("WHEN killSession is called") {

            it("THEN the call is recorded") {
                val facade = FakeAgentFacade()
                facade.killSession(buildHandle())
                facade.killSessionCalls shouldHaveSize 1
            }

            it("THEN the recorded handle matches what was passed") {
                val facade = FakeAgentFacade()
                val handle = buildHandle()
                facade.killSession(handle)
                facade.killSessionCalls.first() shouldBe handle
            }
        }
    }

    describe("GIVEN a FakeAgentFacade with no calls made") {

        it("THEN spawnCalls is empty") {
            FakeAgentFacade().spawnCalls.shouldBeEmpty()
        }

        it("THEN sendPayloadCalls is empty") {
            FakeAgentFacade().sendPayloadCalls.shouldBeEmpty()
        }

        it("THEN readContextWindowStateCalls is empty") {
            FakeAgentFacade().readContextWindowStateCalls.shouldBeEmpty()
        }

        it("THEN killSessionCalls is empty") {
            FakeAgentFacade().killSessionCalls.shouldBeEmpty()
        }
    }

    // ── Tests: Crashed and SelfCompacted signals ───────────────────────

    describe("GIVEN a FakeAgentFacade programmed to return Crashed") {
        describe("WHEN sendPayloadAndAwaitSignal is called") {
            it("THEN it returns Crashed with the programmed details") {
                val facade = FakeAgentFacade()
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Crashed("agent unresponsive")
                }

                val signal = facade.sendPayloadAndAwaitSignal(
                    buildHandle(), buildPayload()
                )
                signal shouldBe AgentSignal.Crashed("agent unresponsive")
            }
        }
    }

    describe("GIVEN a FakeAgentFacade programmed to return SelfCompacted") {
        describe("WHEN sendPayloadAndAwaitSignal is called") {
            it("THEN it returns SelfCompacted") {
                val facade = FakeAgentFacade()
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.SelfCompacted
                }

                val signal = facade.sendPayloadAndAwaitSignal(
                    buildHandle(), buildPayload()
                )
                signal shouldBe AgentSignal.SelfCompacted
            }
        }
    }

    // ── Tests: FailWorkflow signal ─────────────────────────────────────

    describe("GIVEN a FakeAgentFacade programmed to return FailWorkflow") {
        describe("WHEN sendPayloadAndAwaitSignal is called") {
            it("THEN it returns FailWorkflow with the programmed reason") {
                val facade = FakeAgentFacade()
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.FailWorkflow("missing dependency")
                }

                val signal = facade.sendPayloadAndAwaitSignal(
                    buildHandle(), buildPayload()
                )
                signal shouldBe AgentSignal.FailWorkflow("missing dependency")
            }
        }
    }

    // ── Tests: Virtual time interop ────────────────────────────────────

    describe("GIVEN runTest with a FakeAgentFacade simulating delay") {
        describe("WHEN advanceTimeBy is used to fast-forward past the delay") {

            it("THEN the coroutine completes without real wall-clock wait") {
                runTest {
                    val facade = FakeAgentFacade()
                    facade.onSendPayloadAndAwaitSignal { _, _ ->
                        delay(30.seconds)
                        AgentSignal.Done(DoneResult.COMPLETED)
                    }

                    var signal: AgentSignal? = null
                    launch {
                        signal = facade.sendPayloadAndAwaitSignal(
                            buildHandle(), buildPayload()
                        )
                    }

                    advanceTimeBy(30.seconds)
                    runCurrent()

                    signal shouldBe AgentSignal.Done(DoneResult.COMPLETED)
                }
            }
        }
    }

    describe("GIVEN runTest with TestClock") {
        describe("WHEN both TestClock and coroutine virtual time are advanced") {

            it("THEN TestClock advances independently of coroutine virtual time") {
                runTest {
                    val testClock = TestClock()

                    testClock.now() shouldBe Instant.EPOCH
                    testClock.advance(5.minutes)
                    testClock.now() shouldBe Instant.EPOCH.plusSeconds(300)

                    // Coroutine virtual time is separate
                    testScheduler.currentTime shouldBe 0L
                    advanceTimeBy(1000)
                    testScheduler.currentTime shouldBe 1000L

                    // TestClock unaffected by advanceTimeBy
                    testClock.now() shouldBe Instant.EPOCH.plusSeconds(300)
                }
            }
        }
    }

    // ── Tests: DispatcherProvider injectability ────────────────────────

    describe("GIVEN a DispatcherProvider backed by StandardTestDispatcher") {
        describe("WHEN a coroutine is launched on the injected dispatcher") {

            it("THEN the coroutine runs on the test dispatcher") {
                runTest {
                    val testDispatcher = StandardTestDispatcher(testScheduler)
                    val provider = DispatcherProvider { testDispatcher }

                    var executed = false
                    launch {
                        withContext(provider.io()) {
                            executed = true
                        }
                    }

                    advanceTimeBy(1)
                    runCurrent()
                    executed shouldBe true
                }
            }
        }
    }
})
