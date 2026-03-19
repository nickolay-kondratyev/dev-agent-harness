package com.glassthought.shepherd.core.session

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
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

internal val noOpCommunicator = object : TmuxCommunicator {
    override suspend fun sendKeys(paneTarget: String, text: String) = Unit
    override suspend fun sendRawKeys(paneTarget: String, keys: String) = Unit
}

internal val noOpExistsChecker = SessionExistenceChecker { false }

internal fun createTestTmuxAgentSession(): TmuxAgentSession {
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

internal fun createTestSessionEntry(
    partName: String = "test-part",
    subPartName: String = "test-sub-part",
    subPartIndex: Int = 0,
    questionQueue: ConcurrentLinkedQueue<UserQuestionContext> = ConcurrentLinkedQueue(),
): SessionEntry = SessionEntry(
    tmuxAgentSession = createTestTmuxAgentSession(),
    partName = partName,
    subPartName = subPartName,
    subPartIndex = subPartIndex,
    signalDeferred = CompletableDeferred<AgentSignal>(),
    lastActivityTimestamp = AtomicReference(Instant.now()),
    pendingPayloadAck = AtomicReference(null),
    questionQueue = questionQueue,
)

internal fun createTestUserQuestionContext(): UserQuestionContext = UserQuestionContext(
    question = "test question?",
    partName = "test-part",
    subPartName = "test-sub-part",
    subPartRole = SubPartRole.DOER,
    handshakeGuid = HandshakeGuid.generate(),
)
