package com.glassthought.shepherd.core.session

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
import com.glassthought.shepherd.core.question.UserQuestionContext
import com.glassthought.shepherd.core.state.SubPartRole
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

class SessionEntryTest : AsgardDescribeSpec({

    describe("GIVEN SessionEntry with empty questionQueue") {
        val entry = createTestSessionEntry(subPartIndex = 0)

        it("THEN isQAPending is false") {
            entry.isQAPending shouldBe false
        }
    }

    describe("GIVEN SessionEntry with non-empty questionQueue") {
        val queue = ConcurrentLinkedQueue<UserQuestionContext>()
        queue.add(createTestUserQuestionContext())
        val entry = createTestSessionEntry(subPartIndex = 0, questionQueue = queue)

        it("THEN isQAPending is true") {
            entry.isQAPending shouldBe true
        }
    }

    describe("GIVEN SessionEntry with subPartIndex 0") {
        val entry = createTestSessionEntry(subPartIndex = 0)

        it("THEN role is DOER") {
            entry.role shouldBe SubPartRole.DOER
        }
    }

    describe("GIVEN SessionEntry with subPartIndex 1") {
        val entry = createTestSessionEntry(subPartIndex = 1)

        it("THEN role is REVIEWER") {
            entry.role shouldBe SubPartRole.REVIEWER
        }
    }

    describe("GIVEN SessionEntry with empty queue") {
        val entry = createTestSessionEntry(subPartIndex = 0)

        describe("WHEN question added to queue") {
            entry.questionQueue.add(createTestUserQuestionContext())

            it("THEN isQAPending becomes true") {
                entry.isQAPending shouldBe true
            }
        }
    }

    describe("GIVEN SessionEntry with questions in queue") {
        val queue = ConcurrentLinkedQueue<UserQuestionContext>()
        queue.add(createTestUserQuestionContext())
        val entry = createTestSessionEntry(subPartIndex = 0, questionQueue = queue)

        describe("WHEN queue is drained") {
            entry.questionQueue.clear()

            it("THEN isQAPending becomes false") {
                entry.isQAPending shouldBe false
            }
        }
    }
})

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
    subPartIndex: Int,
    questionQueue: ConcurrentLinkedQueue<UserQuestionContext> = ConcurrentLinkedQueue(),
): SessionEntry = SessionEntry(
    tmuxAgentSession = createTestTmuxAgentSession(),
    partName = "test-part",
    subPartName = "test-sub-part",
    subPartIndex = subPartIndex,
    signalDeferred = CompletableDeferred<AgentSignal>(),
    lastActivityTimestamp = Instant.now(),
    pendingPayloadAck = null,
    questionQueue = questionQueue,
)

private fun createTestUserQuestionContext(): UserQuestionContext = UserQuestionContext(
    question = "test question?",
    partName = "test-part",
    subPartName = "test-sub-part",
    subPartRole = SubPartRole.DOER,
    handshakeGuid = HandshakeGuid.generate(),
)
