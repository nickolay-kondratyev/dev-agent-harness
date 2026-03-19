package com.glassthought.shepherd.core.question

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.agent.tmux.SessionExistenceChecker
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.server.AckedPayloadSender
import com.glassthought.shepherd.core.session.PendingQuestion
import com.glassthought.shepherd.core.session.SessionEntry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import com.glassthought.shepherd.core.session.UserQuestionContext as SessionUserQuestionContext
import com.glassthought.shepherd.core.state.SubPartRole

class QaDrainAndDeliverUseCaseTest : AsgardDescribeSpec({

    describe("GIVEN single question in queue") {
        val tempDir = Files.createTempDirectory("qa-drain-test")
        val fakeHandler = FakeUserQuestionHandler(listOf("Answer one."))
        val fakeSender = RecordingAckedPayloadSender()
        val useCase = QaDrainAndDeliverUseCase(
            userQuestionHandler = fakeHandler,
            qaAnswersFileWriter = QaAnswersFileWriterImpl(),
            ackedPayloadSender = fakeSender,
            outFactory = outFactory,
        )
        val entry = createTestSessionEntry(
            questions = listOf(createPendingQuestion("Question one?")),
        )

        describe("WHEN drain-and-deliver is called") {
            useCase.drainAndDeliver(entry, tempDir)

            it("THEN handler was called once") {
                fakeHandler.callCount shouldBe 1
            }

            it("THEN qa_answers.md exists with 1 QA pair") {
                val content = tempDir.resolve("qa_answers.md").toFile().readText()
                content shouldContain "### Question 1"
            }

            it("THEN qa_answers.md does not contain Question 2") {
                val content = tempDir.resolve("qa_answers.md").toFile().readText()
                (content.contains("### Question 2")) shouldBe false
            }

            it("THEN AckedPayloadSender was called once") {
                fakeSender.calls.size shouldBe 1
            }

            it("THEN payload references qa_answers.md path") {
                fakeSender.calls[0].payloadContent shouldContain "qa_answers.md"
            }
        }
    }

    describe("GIVEN 3 questions in queue") {
        val tempDir = Files.createTempDirectory("qa-drain-test")
        val fakeHandler = FakeUserQuestionHandler(listOf("Answer A.", "Answer B.", "Answer C."))
        val fakeSender = RecordingAckedPayloadSender()
        val useCase = QaDrainAndDeliverUseCase(
            userQuestionHandler = fakeHandler,
            qaAnswersFileWriter = QaAnswersFileWriterImpl(),
            ackedPayloadSender = fakeSender,
            outFactory = outFactory,
        )
        val entry = createTestSessionEntry(
            questions = listOf(
                createPendingQuestion("Q1?"),
                createPendingQuestion("Q2?"),
                createPendingQuestion("Q3?"),
            ),
        )

        describe("WHEN drain-and-deliver is called") {
            useCase.drainAndDeliver(entry, tempDir)

            it("THEN handler was called 3 times") {
                fakeHandler.callCount shouldBe 3
            }

            it("THEN qa_answers.md contains Question 3") {
                val content = tempDir.resolve("qa_answers.md").toFile().readText()
                content shouldContain "### Question 3"
            }

            it("THEN AckedPayloadSender was called once (batch delivery)") {
                fakeSender.calls.size shouldBe 1
            }

            it("THEN questions were received in order") {
                fakeHandler.receivedQuestions shouldBe listOf("Q1?", "Q2?", "Q3?")
            }
        }
    }

    describe("GIVEN question arrives during answer collection") {
        val tempDir = Files.createTempDirectory("qa-drain-test")
        val entry = createTestSessionEntry(
            questions = listOf(createPendingQuestion("Initial question?")),
        )

        // Handler that adds a new question to the queue when answering the first one
        val lateArrivalHandler = object : UserQuestionHandler {
            val receivedQuestions = mutableListOf<String>()
            var callCount = 0

            override suspend fun handleQuestion(context: UserQuestionContext): String {
                callCount++
                receivedQuestions.add(context.question)
                if (callCount == 1) {
                    // Simulate a new question arriving while we answer the first
                    entry.questionQueue.add(createPendingQuestion("Late arrival question?"))
                }
                return "Answer ${callCount}."
            }
        }

        val fakeSender = RecordingAckedPayloadSender()
        val useCase = QaDrainAndDeliverUseCase(
            userQuestionHandler = lateArrivalHandler,
            qaAnswersFileWriter = QaAnswersFileWriterImpl(),
            ackedPayloadSender = fakeSender,
            outFactory = outFactory,
        )

        describe("WHEN drain-and-deliver is called") {
            useCase.drainAndDeliver(entry, tempDir)

            it("THEN both questions were processed") {
                lateArrivalHandler.callCount shouldBe 2
            }

            it("THEN late arrival question was also included") {
                lateArrivalHandler.receivedQuestions[1] shouldBe "Late arrival question?"
            }

            it("THEN qa_answers.md contains both answers") {
                val content = tempDir.resolve("qa_answers.md").toFile().readText()
                content shouldContain "### Question 2"
            }

            it("THEN only one delivery was made") {
                fakeSender.calls.size shouldBe 1
            }
        }
    }

    describe("GIVEN empty question queue") {
        val tempDir = Files.createTempDirectory("qa-drain-test")
        val fakeHandler = FakeUserQuestionHandler(emptyList())
        val fakeSender = RecordingAckedPayloadSender()
        val useCase = QaDrainAndDeliverUseCase(
            userQuestionHandler = fakeHandler,
            qaAnswersFileWriter = QaAnswersFileWriterImpl(),
            ackedPayloadSender = fakeSender,
            outFactory = outFactory,
        )
        val entry = createTestSessionEntry(questions = emptyList())

        describe("WHEN drain-and-deliver is called") {
            useCase.drainAndDeliver(entry, tempDir)

            it("THEN handler was never called") {
                fakeHandler.callCount shouldBe 0
            }

            it("THEN AckedPayloadSender was never called") {
                fakeSender.calls.size shouldBe 0
            }

            it("THEN no file was written") {
                tempDir.resolve("qa_answers.md").toFile().exists() shouldBe false
            }
        }
    }

    describe("GIVEN answers collected") {
        val tempDir = Files.createTempDirectory("qa-drain-test")
        val fakeHandler = FakeUserQuestionHandler(
            listOf("Use CSS Grid with a mobile-first approach.", "Not in V1, focus on core functionality."),
        )
        val fakeSender = RecordingAckedPayloadSender()
        val useCase = QaDrainAndDeliverUseCase(
            userQuestionHandler = fakeHandler,
            qaAnswersFileWriter = QaAnswersFileWriterImpl(),
            ackedPayloadSender = fakeSender,
            outFactory = outFactory,
        )
        val entry = createTestSessionEntry(
            questions = listOf(
                createPendingQuestion("How should I handle the responsive layout for mobile devices?"),
                createPendingQuestion("Should I add dark mode support?"),
            ),
        )

        describe("WHEN drain-and-deliver is called") {
            useCase.drainAndDeliver(entry, tempDir)
            val content = tempDir.resolve("qa_answers.md").toFile().readText()

            it("THEN qa_answers.md matches spec format with header") {
                content shouldContain "## Q&A Answers"
            }

            it("THEN question 1 blockquote is correct") {
                content shouldContain "> How should I handle the responsive layout for mobile devices?"
            }

            it("THEN answer 1 has bold prefix") {
                content shouldContain "**Answer:** Use CSS Grid with a mobile-first approach."
            }

            it("THEN question 2 blockquote is correct") {
                content shouldContain "> Should I add dark mode support?"
            }

            it("THEN answer 2 has bold prefix") {
                content shouldContain "**Answer:** Not in V1, focus on core functionality."
            }
        }
    }

    describe("GIVEN delivery") {
        val tempDir = Files.createTempDirectory("qa-drain-test")
        val fakeHandler = FakeUserQuestionHandler(listOf("Some answer."))
        val fakeSender = RecordingAckedPayloadSender()
        val useCase = QaDrainAndDeliverUseCase(
            userQuestionHandler = fakeHandler,
            qaAnswersFileWriter = QaAnswersFileWriterImpl(),
            ackedPayloadSender = fakeSender,
            outFactory = outFactory,
        )
        val entry = createTestSessionEntry(
            questions = listOf(createPendingQuestion("Some question?")),
        )

        describe("WHEN drain-and-deliver is called") {
            useCase.drainAndDeliver(entry, tempDir)

            it("THEN AckedPayloadSender receives correct tmuxAgentSession") {
                fakeSender.calls[0].tmuxSession shouldBe entry.tmuxAgentSession
            }

            it("THEN AckedPayloadSender receives correct sessionEntry") {
                fakeSender.calls[0].sessionEntry shouldBe entry
            }

            it("THEN payload content starts with Read QA answers at") {
                fakeSender.calls[0].payloadContent shouldContain "Read QA answers at"
            }
        }
    }
})

// ── Fakes ─────────────────────────────────────────────────────────────────

/**
 * Fake [UserQuestionHandler] that returns pre-configured answers in order.
 * Records received questions for assertion.
 */
private class FakeUserQuestionHandler(
    private val answers: List<String>,
) : UserQuestionHandler {
    val receivedQuestions = mutableListOf<String>()
    var callCount = 0

    override suspend fun handleQuestion(context: UserQuestionContext): String {
        val answer = answers[callCount]
        receivedQuestions.add(context.question)
        callCount++
        return answer
    }
}

/**
 * Records all calls to [sendAndAwaitAck] for assertion.
 */
private class RecordingAckedPayloadSender : AckedPayloadSender {
    data class SendCall(
        val tmuxSession: TmuxAgentSession,
        val sessionEntry: SessionEntry,
        val payloadContent: String,
    )

    val calls = mutableListOf<SendCall>()

    override suspend fun sendAndAwaitAck(
        tmuxSession: TmuxAgentSession,
        sessionEntry: SessionEntry,
        payloadContent: String,
    ) {
        calls.add(SendCall(tmuxSession, sessionEntry, payloadContent))
    }
}

// ── Test Helpers ──────────────────────────────────────────────────────────

private val noOpCommunicator = object : TmuxCommunicator {
    override suspend fun sendKeys(paneTarget: String, text: String) = Unit
    override suspend fun sendRawKeys(paneTarget: String, keys: String) = Unit
}
private val noOpExistsChecker = SessionExistenceChecker { false }

private fun createTestTmuxAgentSession(): TmuxAgentSession {
    val tmuxSession = TmuxSession(
        name = TmuxSessionName("test-session"),
        paneTarget = "test-session:0.0",
        communicator = noOpCommunicator,
        existsChecker = noOpExistsChecker,
    )
    val resumableId = ResumableAgentSessionId(
        handshakeGuid = HandshakeGuid.generate(),
        agentType = AgentType.CLAUDE_CODE,
        sessionId = "test-session-id",
        model = "test-model",
    )
    return TmuxAgentSession(tmuxSession = tmuxSession, resumableAgentSessionId = resumableId)
}

private fun createTestSessionEntry(
    questions: List<PendingQuestion>,
): SessionEntry {
    val queue = ConcurrentLinkedQueue<PendingQuestion>()
    questions.forEach { queue.add(it) }
    return SessionEntry(
        tmuxAgentSession = createTestTmuxAgentSession(),
        partName = "test-part",
        subPartName = "test-sub-part",
        subPartIndex = 0,
        signalDeferred = CompletableDeferred<AgentSignal>(),
        lastActivityTimestamp = Instant.now(),
        pendingPayloadAck = null,
        questionQueue = queue,
    )
}

private fun createPendingQuestion(question: String): PendingQuestion = PendingQuestion(
    question = question,
    context = SessionUserQuestionContext(
        question = question,
        partName = "test-part",
        subPartName = "test-sub-part",
        subPartRole = SubPartRole.DOER,
        handshakeGuid = HandshakeGuid.generate(),
    ),
)
