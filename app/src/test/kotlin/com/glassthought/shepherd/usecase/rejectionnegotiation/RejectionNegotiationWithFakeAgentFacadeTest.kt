package com.glassthought.shepherd.usecase.rejectionnegotiation

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.FakeAgentFacade
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwaitImpl
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path
import java.time.Instant

/**
 * Wired integration tests: FakeAgentFacade → ReInstructAndAwaitImpl → RejectionNegotiationUseCaseImpl.
 *
 * Unlike [RejectionNegotiationUseCaseImplTest] which fakes ReInstructAndAwait directly,
 * these tests exercise the full composition stack with only AgentFacade faked out.
 */
class RejectionNegotiationWithFakeAgentFacadeTest : AsgardDescribeSpec({

    // ── Helpers ────────────────────────────────────────────────────────

    fun buildHandle(name: String): SpawnedAgentHandle {
        val guid = HandshakeGuid("handshake.$name")
        return SpawnedAgentHandle(
            guid = guid,
            sessionId = ResumableAgentSessionId(
                handshakeGuid = guid,
                agentType = AgentType.CLAUDE_CODE,
                sessionId = "session-$name",
                model = "test-model",
            ),
            lastActivityTimestamp = Instant.EPOCH,
        )
    }

    val feedbackFilePath = Path.of("/tmp/feedback/critical__fix-bug.md")

    val rejectedFeedbackContent = """
        |## Feedback Item: Fix the bug in parser
        |
        |The parser does not handle edge case X.
        |
        |## Resolution: REJECTED
        |
        |This is not a real bug — the behavior is by design per spec section 3.2.
    """.trimMargin()

    val addressedFeedbackContent = """
        |## Feedback Item: Fix the bug in parser
        |
        |The parser does not handle edge case X.
        |
        |## Resolution: ADDRESSED
        |
        |Fixed the parser to handle edge case X.
    """.trimMargin()

    val skippedFeedbackContent = """
        |## Feedback Item: Fix the bug in parser
        |
        |## Resolution: SKIPPED
        |
        |Not relevant to current scope.
    """.trimMargin()

    val invalidMarkerFeedbackContent = """
        |## Feedback Item: Fix the bug in parser
        |
        |## Resolution: MAYBE_LATER
        |
        |Will consider in the future.
    """.trimMargin()

    /**
     * Builds a [FeedbackFileReader] that returns different content on successive reads.
     * First call returns [first], subsequent calls return [second].
     */
    fun buildFileReader(first: String, second: String? = null): FeedbackFileReader {
        var callCount = 0
        return FeedbackFileReader { _ ->
            callCount++
            if (callCount == 1) first else (second ?: first)
        }
    }

    /**
     * [InstructionFileWriter] that returns a deterministic path based on the label.
     * The written content is not verified here — the facade handler receives the path
     * through [AgentPayload.instructionFilePath].
     */
    val fakeInstructionFileWriter = InstructionFileWriter { _, label ->
        Path.of("/tmp/instructions/$label.md")
    }

    /**
     * Builds the full wired stack: FakeAgentFacade → ReInstructAndAwaitImpl → RejectionNegotiationUseCaseImpl.
     */
    fun buildSut(
        fakeFacade: FakeAgentFacade,
        feedbackFileReader: FeedbackFileReader,
    ): RejectionNegotiationUseCaseImpl {
        val reInstructAndAwait = ReInstructAndAwaitImpl(agentFacade = fakeFacade)
        return RejectionNegotiationUseCaseImpl(
            reInstructAndAwait = reInstructAndAwait,
            feedbackFileReader = feedbackFileReader,
            instructionFileWriter = fakeInstructionFileWriter,
            outFactory = outFactory,
        )
    }

    // ── Test 1: Reviewer signals Done(PASS) → Accepted ─────────────────

    describe("GIVEN reviewer signals Done(PASS) through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { handle, _ ->
            when (handle.guid) {
                reviewerHandle.guid -> AgentSignal.Done(DoneResult.PASS)
                else -> error("Unexpected handle: ${handle.guid}")
            }
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.Accepted") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.Accepted>()
            }

            it("THEN payload forwarded to reviewer contains rejection reasoning") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                val reviewerPayload = fakeFacade.sendPayloadCalls.first()
                reviewerPayload.handle shouldBe reviewerHandle
            }
        }
    }

    // ── Test 2: Reviewer NEEDS_ITERATION → doer complies → AddressedAfterInsistence ──

    describe("GIVEN reviewer signals NEEDS_ITERATION and doer writes ADDRESSED through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { handle, _ ->
            when (handle.guid) {
                reviewerHandle.guid -> AgentSignal.Done(DoneResult.NEEDS_ITERATION)
                doerHandle.guid -> AgentSignal.Done(DoneResult.COMPLETED)
                else -> error("Unexpected handle: ${handle.guid}")
            }
        }

        val fileReader = buildFileReader(rejectedFeedbackContent, addressedFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AddressedAfterInsistence") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AddressedAfterInsistence>()
            }

            it("THEN reviewer handle receives first payload") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                fakeFacade.sendPayloadCalls[0].handle shouldBe reviewerHandle
            }

            it("THEN doer handle receives second payload") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                fakeFacade.sendPayloadCalls[1].handle shouldBe doerHandle
            }
        }
    }

    // ── Test 3: Reviewer signals Done(COMPLETED) — unexpected ───────────

    describe("GIVEN reviewer signals unexpected Done(COMPLETED) through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
            AgentSignal.Done(DoneResult.COMPLETED)
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details mention unexpected COMPLETED") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldContain "unexpected COMPLETED"
            }
        }
    }

    // ── Test 4: Reviewer insists → doer writes SKIPPED → AgentCrashed ───

    describe("GIVEN reviewer insists and doer writes SKIPPED through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { handle, _ ->
            when (handle.guid) {
                reviewerHandle.guid -> AgentSignal.Done(DoneResult.NEEDS_ITERATION)
                doerHandle.guid -> AgentSignal.Done(DoneResult.COMPLETED)
                else -> error("Unexpected handle: ${handle.guid}")
            }
        }

        val fileReader = buildFileReader(rejectedFeedbackContent, skippedFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details mention SKIPPED") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldContain "SKIPPED"
            }
        }
    }

    // ── Test 5: Reviewer insists → doer writes invalid resolution marker ──

    describe("GIVEN reviewer insists and doer writes invalid resolution marker through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { handle, _ ->
            when (handle.guid) {
                reviewerHandle.guid -> AgentSignal.Done(DoneResult.NEEDS_ITERATION)
                doerHandle.guid -> AgentSignal.Done(DoneResult.COMPLETED)
                else -> error("Unexpected handle: ${handle.guid}")
            }
        }

        val fileReader = buildFileReader(rejectedFeedbackContent, invalidMarkerFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details mention invalid resolution marker") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldContain "invalid resolution marker"
            }
        }
    }

    // ── Test 6: Reviewer signals Crashed → AgentCrashed propagates ──────

    describe("GIVEN reviewer signals Crashed through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
            AgentSignal.Crashed("reviewer process died unexpectedly")
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details flow unchanged from facade") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldBe "reviewer process died unexpectedly"
            }
        }
    }

    // ── Test 7: Reviewer signals FailWorkflow → FailedWorkflow propagates ──

    describe("GIVEN reviewer signals FailWorkflow through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { _, _ ->
            AgentSignal.FailWorkflow("ticket is impossible to complete")
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.FailedWorkflow") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.FailedWorkflow>()
            }

            it("THEN reason flows unchanged from facade") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.FailedWorkflow).reason shouldBe "ticket is impossible to complete"
            }
        }
    }

    // ── Test 8: Verify sendPayloadCalls recording after insistence flow ──

    describe("GIVEN insistence flow completes through full wired stack") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeFacade = FakeAgentFacade()
        fakeFacade.onSendPayloadAndAwaitSignal { handle, _ ->
            when (handle.guid) {
                reviewerHandle.guid -> AgentSignal.Done(DoneResult.NEEDS_ITERATION)
                doerHandle.guid -> AgentSignal.Done(DoneResult.COMPLETED)
                else -> error("Unexpected handle: ${handle.guid}")
            }
        }

        val fileReader = buildFileReader(rejectedFeedbackContent, addressedFeedbackContent)
        val sut = buildSut(fakeFacade, fileReader)

        describe("WHEN execute completes") {
            it("THEN sendPayloadCalls has exactly 2 entries") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                fakeFacade.sendPayloadCalls.size shouldBe 2
            }

            it("THEN first call is to reviewer handle") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                fakeFacade.sendPayloadCalls[0].handle shouldBe reviewerHandle
            }

            it("THEN second call is to doer handle") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                fakeFacade.sendPayloadCalls[1].handle shouldBe doerHandle
            }
        }
    }
})
