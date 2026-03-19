package com.glassthought.shepherd.usecase.reinstructandawait

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.facade.AgentPayload
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.FakeAgentFacade
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.data.AgentType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path
import java.time.Instant
import java.util.ArrayDeque

class ReInstructAndAwaitImplTest : AsgardDescribeSpec({

    // ── Helpers ────────────────────────────────────────────────────────

    fun buildHandle(guidValue: String = "handshake.test-reinstructandawait"): SpawnedAgentHandle {
        val guid = HandshakeGuid(guidValue)
        return SpawnedAgentHandle(
            guid = guid,
            sessionId = ResumableAgentSessionId(
                handshakeGuid = guid,
                agentType = AgentType.CLAUDE_CODE,
                sessionId = "session-1",
                model = "test-model",
            ),
            lastActivityTimestamp = Instant.EPOCH,
        )
    }

    fun buildSut(fakeAgentFacade: FakeAgentFacade): ReInstructAndAwaitImpl =
        ReInstructAndAwaitImpl(
            agentFacade = fakeAgentFacade,
        )

    val testMessage = "/tmp/instruction.md"

    // ── Tests ──────────────────────────────────────────────────────────

    describe("GIVEN agent responds with Done(COMPLETED)") {
        describe("WHEN execute is called") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.Done(DoneResult.COMPLETED)
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN returns Responded") {
                val outcome = sut.execute(handle, testMessage)
                outcome.shouldBeInstanceOf<ReInstructOutcome.Responded>()
            }

            it("THEN Responded contains Done(COMPLETED)") {
                val outcome = sut.execute(handle, testMessage)
                (outcome as ReInstructOutcome.Responded).signal.result shouldBe DoneResult.COMPLETED
            }
        }
    }

    describe("GIVEN agent responds with Done(PASS)") {
        describe("WHEN execute is called") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.Done(DoneResult.PASS)
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN returns Responded with Done(PASS)") {
                val outcome = sut.execute(handle, testMessage)
                val responded = outcome.shouldBeInstanceOf<ReInstructOutcome.Responded>()
                responded.signal.result shouldBe DoneResult.PASS
            }
        }
    }

    describe("GIVEN agent responds with Done(NEEDS_ITERATION)") {
        describe("WHEN execute is called") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.Done(DoneResult.NEEDS_ITERATION)
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN returns Responded with Done(NEEDS_ITERATION)") {
                val outcome = sut.execute(handle, testMessage)
                val responded = outcome.shouldBeInstanceOf<ReInstructOutcome.Responded>()
                responded.signal.result shouldBe DoneResult.NEEDS_ITERATION
            }
        }
    }

    describe("GIVEN agent crashes") {
        describe("WHEN execute is called") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.Crashed("agent process died unexpectedly")
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN returns Crashed") {
                val outcome = sut.execute(handle, testMessage)
                outcome.shouldBeInstanceOf<ReInstructOutcome.Crashed>()
            }

            it("THEN Crashed contains the crash details") {
                val outcome = sut.execute(handle, testMessage)
                (outcome as ReInstructOutcome.Crashed).details shouldBe "agent process died unexpectedly"
            }
        }
    }

    describe("GIVEN agent signals fail-workflow") {
        describe("WHEN execute is called") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.FailWorkflow("ticket is impossible to complete")
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN returns FailedWorkflow") {
                val outcome = sut.execute(handle, testMessage)
                outcome.shouldBeInstanceOf<ReInstructOutcome.FailedWorkflow>()
            }

            it("THEN FailedWorkflow contains the reason") {
                val outcome = sut.execute(handle, testMessage)
                (outcome as ReInstructOutcome.FailedWorkflow).reason shouldBe "ticket is impossible to complete"
            }
        }
    }

    describe("GIVEN agent signals SelfCompacted (unexpected)") {
        describe("WHEN execute is called") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.SelfCompacted
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN returns Crashed (SelfCompacted should not reach this class)") {
                val outcome = sut.execute(handle, testMessage)
                outcome.shouldBeInstanceOf<ReInstructOutcome.Crashed>()
            }

            it("THEN Crashed details mention unexpected SelfCompacted") {
                val outcome = sut.execute(handle, testMessage)
                val crashed = outcome as ReInstructOutcome.Crashed
                crashed.details shouldBe "Unexpected SelfCompacted signal reached ReInstructAndAwait — " +
                    "facade should handle self-compaction transparently"
            }
        }
    }

    describe("GIVEN execute is called with a message") {
        describe("WHEN the facade receives the call") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.Done(DoneResult.COMPLETED)
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN the message is converted to AgentPayload with correct path") {
                sut.execute(handle, testMessage)
                val sentPayload = fakeFacade.sendPayloadCalls.first().payload
                sentPayload shouldBe AgentPayload(instructionFilePath = Path.of(testMessage))
            }

            it("THEN the correct handle is passed to the facade") {
                sut.execute(handle, testMessage)
                fakeFacade.sendPayloadCalls.first().handle shouldBe handle
            }
        }
    }

    // ── Scenario 1: Sequential execute calls record each in order ────────

    describe("GIVEN agent responds NEEDS_ITERATION then COMPLETED on sequential calls") {

        val firstMessage = "/tmp/iteration-1.md"
        val secondMessage = "/tmp/iteration-2.md"

        fun buildSequentialFacade(): FakeAgentFacade {
            val responses = ArrayDeque<AgentSignal>()
            responses.add(AgentSignal.Done(DoneResult.NEEDS_ITERATION))
            responses.add(AgentSignal.Done(DoneResult.COMPLETED))
            val facade = FakeAgentFacade()
            facade.onSendPayloadAndAwaitSignal { _, _ -> responses.remove() }
            return facade
        }

        describe("WHEN execute is called twice on the same handle") {
            it("THEN sendPayloadCalls has exactly 2 entries") {
                val fakeFacade = buildSequentialFacade()
                val sut = buildSut(fakeFacade)
                val handle = buildHandle()
                sut.execute(handle, firstMessage)
                sut.execute(handle, secondMessage)
                fakeFacade.sendPayloadCalls.size shouldBe 2
            }

            it("THEN first call payload has first message path") {
                val fakeFacade = buildSequentialFacade()
                val sut = buildSut(fakeFacade)
                val handle = buildHandle()
                sut.execute(handle, firstMessage)
                sut.execute(handle, secondMessage)
                fakeFacade.sendPayloadCalls[0].payload.instructionFilePath shouldBe Path.of(firstMessage)
            }

            it("THEN second call payload has second message path") {
                val fakeFacade = buildSequentialFacade()
                val sut = buildSut(fakeFacade)
                val handle = buildHandle()
                sut.execute(handle, firstMessage)
                sut.execute(handle, secondMessage)
                fakeFacade.sendPayloadCalls[1].payload.instructionFilePath shouldBe Path.of(secondMessage)
            }

            it("THEN first call references the same handle") {
                val fakeFacade = buildSequentialFacade()
                val sut = buildSut(fakeFacade)
                val handle = buildHandle()
                sut.execute(handle, firstMessage)
                sut.execute(handle, secondMessage)
                fakeFacade.sendPayloadCalls[0].handle shouldBe handle
            }

            it("THEN second call references the same handle") {
                val fakeFacade = buildSequentialFacade()
                val sut = buildSut(fakeFacade)
                val handle = buildHandle()
                sut.execute(handle, firstMessage)
                sut.execute(handle, secondMessage)
                fakeFacade.sendPayloadCalls[1].handle shouldBe handle
            }
        }
    }

    // ── Scenario 2: Explicit Path equality for instructionFilePath ───────

    describe("GIVEN execute is called with a message path") {
        describe("WHEN verifying payload instructionFilePath with explicit Path equality") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                AgentSignal.Done(DoneResult.COMPLETED)
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()
            val messagePath = "/workspace/instructions/step-3.md"

            it("THEN instructionFilePath equals Path.of(message)") {
                sut.execute(handle, messagePath)
                fakeFacade.sendPayloadCalls[0].payload.instructionFilePath shouldBe Path.of(messagePath)
            }
        }
    }

    // ── Scenario 3: Multiple handles — identity preserved ────────────────

    describe("GIVEN two different SpawnedAgentHandles") {
        describe("WHEN execute is called once per handle") {
            val doerHandle = buildHandle(guidValue = "handshake.doer")
            val reviewerHandle = buildHandle(guidValue = "handshake.reviewer")

            it("THEN first sendPayloadCall references the doer handle") {
                val fakeFacade = FakeAgentFacade()
                fakeFacade.onSendPayloadAndAwaitSignal { _, _ -> AgentSignal.Done(DoneResult.COMPLETED) }
                val sut = buildSut(fakeFacade)
                sut.execute(doerHandle, "/tmp/doer-instruction.md")
                sut.execute(reviewerHandle, "/tmp/reviewer-instruction.md")
                fakeFacade.sendPayloadCalls[0].handle shouldBe doerHandle
            }

            it("THEN second sendPayloadCall references the reviewer handle") {
                val fakeFacade = FakeAgentFacade()
                fakeFacade.onSendPayloadAndAwaitSignal { _, _ -> AgentSignal.Done(DoneResult.COMPLETED) }
                val sut = buildSut(fakeFacade)
                sut.execute(doerHandle, "/tmp/doer-instruction.md")
                sut.execute(reviewerHandle, "/tmp/reviewer-instruction.md")
                fakeFacade.sendPayloadCalls[1].handle shouldBe reviewerHandle
            }
        }
    }

    // ── Scenario 4: AgentFacade throws exception → propagates uncaught ───

    describe("GIVEN AgentFacade throws IllegalStateException") {
        describe("WHEN execute is called") {
            val fakeFacade = FakeAgentFacade()
            fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
                error("facade exploded")
            }
            val sut = buildSut(fakeFacade)
            val handle = buildHandle()

            it("THEN the exception propagates uncaught") {
                val thrown = shouldThrow<IllegalStateException> {
                    sut.execute(handle, testMessage)
                }
                thrown.message shouldBe "facade exploded"
            }
        }
    }
})
