package com.glassthought.shepherd.usecase.rejectionnegotiation

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.facade.AgentPayload
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwait
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructOutcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path
import java.time.Instant

class RejectionNegotiationUseCaseImplTest : AsgardDescribeSpec({

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
     * Builds a [ReInstructAndAwait] that dispatches based on handle identity.
     * Allows programming separate responses for reviewer and doer.
     */
    class FakeReInstructAndAwait : ReInstructAndAwait {
        private val handlers = mutableMapOf<HandshakeGuid, suspend (String) -> ReInstructOutcome>()
        val calls = mutableListOf<Pair<SpawnedAgentHandle, String>>()

        fun onHandle(handle: SpawnedAgentHandle, handler: suspend (String) -> ReInstructOutcome) {
            handlers[handle.guid] = handler
        }

        override suspend fun execute(handle: SpawnedAgentHandle, message: String): ReInstructOutcome {
            calls.add(handle to message)
            val handler = handlers[handle.guid]
                ?: error("FakeReInstructAndAwait: no handler for handle ${handle.guid}")
            return handler(message)
        }
    }

    /**
     * Recording [InstructionFileWriter] that captures written content for verification.
     * Returns a deterministic path based on the label.
     */
    class RecordingInstructionFileWriter : InstructionFileWriter {
        val writtenContent = mutableMapOf<String, String>()

        override suspend fun write(content: String, label: String): Path {
            writtenContent[label] = content
            return Path.of("/tmp/instructions/$label.md")
        }
    }

    val recordingWriter = RecordingInstructionFileWriter()

    fun buildSut(
        reInstructAndAwait: ReInstructAndAwait,
        feedbackFileReader: FeedbackFileReader,
    ): RejectionNegotiationUseCaseImpl =
        RejectionNegotiationUseCaseImpl(
            reInstructAndAwait = reInstructAndAwait,
            feedbackFileReader = feedbackFileReader,
            instructionFileWriter = recordingWriter,
            outFactory = outFactory,
        )

    // ── Test 1: REJECTED → reviewer accepts (pass) → Accepted ──────────

    describe("GIVEN doer rejected and reviewer accepts the rejection") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.PASS))
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.Accepted") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.Accepted>()
            }

            it("THEN reviewer instruction file contains rejection reasoning") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                val reviewerContent = recordingWriter.writtenContent["reviewer-judgment"]!!
                reviewerContent shouldContain "The implementor rejected this feedback item"
                reviewerContent shouldContain "Resolution: REJECTED"
            }
        }
    }

    // ── Test 2: REJECTED → reviewer insists → doer addresses → AddressedAfterInsistence ──

    describe("GIVEN doer rejected and reviewer insists and doer complies") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.NEEDS_ITERATION))
        }
        fakeReInstruct.onHandle(doerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
        }

        // First read returns REJECTED, second read (after doer complies) returns ADDRESSED.
        val fileReader = buildFileReader(rejectedFeedbackContent, addressedFeedbackContent)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AddressedAfterInsistence") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AddressedAfterInsistence>()
            }

            it("THEN doer instruction file contains compliance instruction") {
                sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                val doerContent = recordingWriter.writtenContent["doer-compliance"]!!
                doerContent shouldContain "Reviewer insists"
                doerContent shouldContain "MUST address"
            }
        }
    }

    // ── Test 3: REJECTED → reviewer insists → doer still rejects → AgentCrashed ──

    describe("GIVEN doer rejected and reviewer insists but doer still rejects") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.NEEDS_ITERATION))
        }
        fakeReInstruct.onHandle(doerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
        }

        // Both reads return REJECTED — doer defied authority.
        val fileReader = buildFileReader(rejectedFeedbackContent, rejectedFeedbackContent)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details mention doer defied authority") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldContain "defied authority"
            }
        }
    }

    // ── Test 4: Reviewer crashes during judgment → AgentCrashed ──────────

    describe("GIVEN reviewer crashes during judgment") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.Crashed("reviewer process died")
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details contain reviewer crash info") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldBe "reviewer process died"
            }
        }
    }

    // ── Test 5: Doer crashes during compliance → AgentCrashed ───────────

    describe("GIVEN reviewer insists but doer crashes during compliance") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.NEEDS_ITERATION))
        }
        fakeReInstruct.onHandle(doerHandle) {
            ReInstructOutcome.Crashed("doer process died during compliance")
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details contain doer crash info") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldBe "doer process died during compliance"
            }
        }
    }

    // ── Test 6: Reviewer signals fail-workflow → FailedWorkflow ─────────

    describe("GIVEN reviewer signals fail-workflow during judgment") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.FailedWorkflow("ticket is impossible")
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.FailedWorkflow") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.FailedWorkflow>()
            }

            it("THEN FailedWorkflow contains the reason") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.FailedWorkflow).reason shouldBe "ticket is impossible"
            }
        }
    }

    // ── Test 7: Doer signals fail-workflow during compliance → FailedWorkflow ──

    describe("GIVEN reviewer insists but doer signals fail-workflow during compliance") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.NEEDS_ITERATION))
        }
        fakeReInstruct.onHandle(doerHandle) {
            ReInstructOutcome.FailedWorkflow("cannot address this item")
        }

        val fileReader = buildFileReader(rejectedFeedbackContent)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.FailedWorkflow") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.FailedWorkflow>()
            }

            it("THEN FailedWorkflow contains the doer's reason") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.FailedWorkflow).reason shouldBe "cannot address this item"
            }
        }
    }

    // ── Test 8: Missing resolution marker after compliance → AgentCrashed ──

    describe("GIVEN reviewer insists and doer responds but leaves no resolution marker") {
        val doerHandle = buildHandle("doer")
        val reviewerHandle = buildHandle("reviewer")

        val fakeReInstruct = FakeReInstructAndAwait()
        fakeReInstruct.onHandle(reviewerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.NEEDS_ITERATION))
        }
        fakeReInstruct.onHandle(doerHandle) {
            ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
        }

        val contentWithNoMarker = "Some content without any resolution marker"
        val fileReader = buildFileReader(rejectedFeedbackContent, contentWithNoMarker)
        val sut = buildSut(fakeReInstruct, fileReader)

        describe("WHEN execute is called") {
            it("THEN returns RejectionResult.AgentCrashed") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                result.shouldBeInstanceOf<RejectionResult.AgentCrashed>()
            }

            it("THEN crash details mention missing resolution marker") {
                val result = sut.execute(doerHandle, reviewerHandle, feedbackFilePath)
                (result as RejectionResult.AgentCrashed).details shouldContain "resolution marker"
            }
        }
    }

    // ── Test: Message templates ──────────────────────────────────────────

    describe("GIVEN reviewer judgment message is built") {
        val content = "Some feedback\n## Resolution: REJECTED\nReason: not a real bug"
        val path = Path.of("/feedback/item.md")

        it("THEN message contains the feedback content") {
            val msg = RejectionNegotiationUseCaseImpl.buildReviewerJudgmentMessage(content, path)
            msg shouldContain "not a real bug"
        }

        it("THEN message contains the feedback file path") {
            val msg = RejectionNegotiationUseCaseImpl.buildReviewerJudgmentMessage(content, path)
            msg shouldContain "/feedback/item.md"
        }

        it("THEN message instructs reviewer to choose pass or needs_iteration") {
            val msg = RejectionNegotiationUseCaseImpl.buildReviewerJudgmentMessage(content, path)
            msg shouldContain "done pass"
            msg shouldContain "done needs_iteration"
        }
    }

    describe("GIVEN doer compliance message is built") {
        val path = Path.of("/feedback/item.md")

        it("THEN message states reviewer insists") {
            val msg = RejectionNegotiationUseCaseImpl.buildDoerComplianceMessage(path)
            msg shouldContain "Reviewer insists"
        }

        it("THEN message instructs doer to write ADDRESSED") {
            val msg = RejectionNegotiationUseCaseImpl.buildDoerComplianceMessage(path)
            msg shouldContain "Resolution: ADDRESSED"
        }

        it("THEN message contains the feedback file path") {
            val msg = RejectionNegotiationUseCaseImpl.buildDoerComplianceMessage(path)
            msg shouldContain "/feedback/item.md"
        }
    }
})
