package com.glassthought.shepherd.core.server

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.agent.tmux.SessionExistenceChecker
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.session.SessionEntry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

class AckedPayloadSenderTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

    // --- XML wrapping format tests ---

    describe("GIVEN wrapPayload with a PayloadId and content") {
        val payloadId = PayloadId("a1b2c3d4-3")
        val content = "Read instructions at /path/to/comm/in/instructions.md"
        val wrapped = AckedPayloadSenderImpl.wrapPayload(payloadId, content)

        it("THEN starts with opening XML tag containing payload_id attribute") {
            wrapped shouldStartWith "<${ProtocolVocabulary.PAYLOAD_ACK_TAG} payload_id=\"a1b2c3d4-3\""
        }

        it("THEN opening tag contains MUST_ACK_BEFORE_PROCEEDING with exact ack command") {
            val script = ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT
            val signal = ProtocolVocabulary.Signal.ACK_PAYLOAD
            val expectedAttr = "MUST_ACK_BEFORE_PROCEEDING=\"$script $signal a1b2c3d4-3\""
            wrapped shouldContain expectedAttr
        }

        it("THEN contains the payload content") {
            wrapped shouldContain content
        }

        it("THEN ends with the closing XML tag") {
            wrapped shouldEndWith "</${ProtocolVocabulary.PAYLOAD_ACK_TAG}>"
        }

        it("THEN matches the exact spec format") {
            val expected = "<payload_from_shepherd_must_ack payload_id=\"a1b2c3d4-3\" " +
                "MUST_ACK_BEFORE_PROCEEDING=\"callback_shepherd.signal.sh ack-payload a1b2c3d4-3\">\n" +
                "Read instructions at /path/to/comm/in/instructions.md\n" +
                "</payload_from_shepherd_must_ack>"
            wrapped shouldBe expected
        }
    }

    // --- PayloadId generation uses HandshakeGuid + counter ---

    describe("GIVEN AckedPayloadSenderImpl with counter starting at 1") {
        val handshakeGuid = HandshakeGuid("handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val counter = AtomicInteger(1)
        val spyCommunicator = SpyTmuxCommunicator()
        val tmuxSession = createTestTmuxAgentSession(handshakeGuid, spyCommunicator)
        val sender = AckedPayloadSenderImpl(
            outFactory = outFactory,
            payloadCounter = counter,
            ackTimeout = 200.milliseconds,
            pollInterval = 10.milliseconds,
            maxAttempts = 1,
        )

        describe("WHEN sendAndAwaitAck is called and ACK arrives immediately") {
            val sessionEntry = createTestSessionEntry(tmuxSession)

            // Simulate immediate ACK by clearing pendingPayloadAck on send
            spyCommunicator.onSend = { sessionEntry.pendingPayloadAck.set(null) }

            it("THEN the wrapped payload uses the correct PayloadId from HandshakeGuid + counter") {
                sender.sendAndAwaitAck(tmuxSession, sessionEntry, "test content")
                spyCommunicator.lastSentText!! shouldContain "a1b2c3d4-1"
            }
        }
    }

    // --- PayloadId is set on SessionEntry before send-keys ---

    describe("GIVEN AckedPayloadSenderImpl") {
        val handshakeGuid = HandshakeGuid("handshake.abcdef12-e5f6-7890-abcd-ef1234567890")
        val counter = AtomicInteger(1)
        val spyCommunicator = SpyTmuxCommunicator()
        val tmuxSession = createTestTmuxAgentSession(handshakeGuid, spyCommunicator)
        val sender = AckedPayloadSenderImpl(
            outFactory = outFactory,
            payloadCounter = counter,
            ackTimeout = 200.milliseconds,
            pollInterval = 10.milliseconds,
            maxAttempts = 1,
        )

        describe("WHEN sendAndAwaitAck is called") {
            val sessionEntry = createTestSessionEntry(tmuxSession)
            var payloadAckAtSendTime: PayloadId? = null

            spyCommunicator.onSend = {
                // Capture the pendingPayloadAck value AT the time sendKeys is called
                payloadAckAtSendTime = sessionEntry.pendingPayloadAck.get()
                // Then simulate ACK
                sessionEntry.pendingPayloadAck.set(null)
            }

            it("THEN pendingPayloadAck is set before send-keys is invoked") {
                sender.sendAndAwaitAck(tmuxSession, sessionEntry, "test content")
                payloadAckAtSendTime shouldBe PayloadId("abcdef12-1")
            }
        }
    }

    // --- ACK arrives immediately → returns normally ---

    describe("GIVEN AckedPayloadSenderImpl with immediate ACK") {
        val handshakeGuid = HandshakeGuid("handshake.11111111-e5f6-7890-abcd-ef1234567890")
        val counter = AtomicInteger(1)
        val spyCommunicator = SpyTmuxCommunicator()
        val tmuxSession = createTestTmuxAgentSession(handshakeGuid, spyCommunicator)
        val sender = AckedPayloadSenderImpl(
            outFactory = outFactory,
            payloadCounter = counter,
            ackTimeout = 200.milliseconds,
            pollInterval = 10.milliseconds,
            maxAttempts = 3,
        )

        describe("WHEN ACK arrives immediately") {
            val sessionEntry = createTestSessionEntry(tmuxSession)
            spyCommunicator.onSend = { sessionEntry.pendingPayloadAck.set(null) }

            it("THEN sendAndAwaitAck returns normally") {
                sender.sendAndAwaitAck(tmuxSession, sessionEntry, "test content")
                // If we reach here, no exception was thrown — success
            }

            it("THEN sendKeys was called exactly once") {
                val sendSessionEntry = createTestSessionEntry(tmuxSession)
                spyCommunicator.sendCount = 0
                spyCommunicator.onSend = { sendSessionEntry.pendingPayloadAck.set(null) }
                sender.sendAndAwaitAck(tmuxSession, sendSessionEntry, "test content")
                spyCommunicator.sendCount shouldBe 1
            }
        }
    }

    // --- ACK timeout on first attempt, arrives on retry ---

    describe("GIVEN AckedPayloadSenderImpl where first attempt times out") {
        val handshakeGuid = HandshakeGuid("handshake.22222222-e5f6-7890-abcd-ef1234567890")
        val counter = AtomicInteger(1)
        val spyCommunicator = SpyTmuxCommunicator()
        val tmuxSession = createTestTmuxAgentSession(handshakeGuid, spyCommunicator)
        val sender = AckedPayloadSenderImpl(
            outFactory = outFactory,
            payloadCounter = counter,
            ackTimeout = 50.milliseconds,
            pollInterval = 10.milliseconds,
            maxAttempts = 3,
        )

        describe("WHEN ACK arrives on second attempt") {
            val sessionEntry = createTestSessionEntry(tmuxSession)
            var attemptCount = 0

            spyCommunicator.onSend = {
                attemptCount++
                // Only ACK on second attempt
                if (attemptCount >= 2) {
                    sessionEntry.pendingPayloadAck.set(null)
                }
            }

            it("THEN sendAndAwaitAck returns normally (retried successfully)").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                sender.sendAndAwaitAck(tmuxSession, sessionEntry, "test content")
            }

            it("THEN sendKeys was called twice").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val retrySessionEntry = createTestSessionEntry(tmuxSession)
                spyCommunicator.sendCount = 0
                var retryAttemptCount = 0
                spyCommunicator.onSend = {
                    retryAttemptCount++
                    if (retryAttemptCount >= 2) {
                        retrySessionEntry.pendingPayloadAck.set(null)
                    }
                }
                sender.sendAndAwaitAck(tmuxSession, retrySessionEntry, "test content")
                spyCommunicator.sendCount shouldBe 2
            }
        }
    }

    // --- All retries exhausted → throws ---

    describe("GIVEN AckedPayloadSenderImpl where ACK never arrives") {
        val handshakeGuid = HandshakeGuid("handshake.33333333-e5f6-7890-abcd-ef1234567890")
        val counter = AtomicInteger(1)
        val spyCommunicator = SpyTmuxCommunicator()
        val tmuxSession = createTestTmuxAgentSession(handshakeGuid, spyCommunicator)
        val sender = AckedPayloadSenderImpl(
            outFactory = outFactory,
            payloadCounter = counter,
            ackTimeout = 50.milliseconds,
            pollInterval = 10.milliseconds,
            maxAttempts = 3,
        )

        describe("WHEN all attempts timeout without ACK") {
            val sessionEntry = createTestSessionEntry(tmuxSession)
            // spyCommunicator.onSend is no-op (default) — never clears pendingPayloadAck

            it("THEN throws PayloadAckTimeoutException").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                shouldThrow<PayloadAckTimeoutException> {
                    sender.sendAndAwaitAck(tmuxSession, sessionEntry, "test content")
                }
            }

            it("THEN exception message contains payload ID").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val freshEntry = createTestSessionEntry(tmuxSession)
                val exception = shouldThrow<PayloadAckTimeoutException> {
                    sender.sendAndAwaitAck(tmuxSession, freshEntry, "test content")
                }
                exception.message!! shouldContain "33333333-"
            }

            it("THEN sendKeys was called maxAttempts times").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val countEntry = createTestSessionEntry(tmuxSession)
                spyCommunicator.sendCount = 0
                try {
                    sender.sendAndAwaitAck(tmuxSession, countEntry, "test content")
                } catch (_: PayloadAckTimeoutException) {
                    // expected
                }
                spyCommunicator.sendCount shouldBe 3
            }
        }
    }

    // --- PayloadId generation uses HandshakeGuid + counter (sequential) ---

    describe("GIVEN AckedPayloadSenderImpl with shared counter") {
        val handshakeGuid = HandshakeGuid("handshake.44444444-e5f6-7890-abcd-ef1234567890")
        val counter = AtomicInteger(1)
        val spyCommunicator = SpyTmuxCommunicator()
        val tmuxSession = createTestTmuxAgentSession(handshakeGuid, spyCommunicator)
        val sender = AckedPayloadSenderImpl(
            outFactory = outFactory,
            payloadCounter = counter,
            ackTimeout = 200.milliseconds,
            pollInterval = 10.milliseconds,
            maxAttempts = 1,
        )

        describe("WHEN sendAndAwaitAck is called twice") {
            val sentPayloadIds = mutableListOf<String>()
            spyCommunicator.onSend = { /* no-op, will simulate ACK below */ }

            it("THEN first call uses sequence 1 and second uses sequence 2") {
                val entry1 = createTestSessionEntry(tmuxSession)
                spyCommunicator.onSend = {
                    sentPayloadIds.add(spyCommunicator.lastSentText!!)
                    entry1.pendingPayloadAck.set(null)
                }
                sender.sendAndAwaitAck(tmuxSession, entry1, "first")

                val entry2 = createTestSessionEntry(tmuxSession)
                spyCommunicator.onSend = {
                    sentPayloadIds.add(spyCommunicator.lastSentText!!)
                    entry2.pendingPayloadAck.set(null)
                }
                sender.sendAndAwaitAck(tmuxSession, entry2, "second")

                sentPayloadIds[0] shouldContain "44444444-1"
                sentPayloadIds[1] shouldContain "44444444-2"
            }
        }
    }
},
)

// --- Test helpers ---

/**
 * Spy [TmuxCommunicator] that records sendKeys calls and optionally executes [onSend].
 *
 * [onSend] is invoked synchronously during [sendKeys] — tests use it to simulate
 * immediate ACKs by clearing [SessionEntry.pendingPayloadAck].
 */
private class SpyTmuxCommunicator : TmuxCommunicator {
    var lastSentText: String? = null
    var sendCount: Int = 0
    var onSend: () -> Unit = {}

    override suspend fun sendKeys(paneTarget: String, text: String) {
        lastSentText = text
        sendCount++
        onSend()
    }

    override suspend fun sendRawKeys(paneTarget: String, keys: String) = Unit
}

private val noOpExistsChecker = SessionExistenceChecker { false }

private fun createTestTmuxAgentSession(
    handshakeGuid: HandshakeGuid,
    communicator: TmuxCommunicator,
): TmuxAgentSession {
    val tmuxSession = TmuxSession(
        name = TmuxSessionName("test-session"),
        paneTarget = "test-session:0.0",
        communicator = communicator,
        existsChecker = noOpExistsChecker,
    )
    val resumableId = ResumableAgentSessionId(
        handshakeGuid = handshakeGuid,
        agentType = AgentType.CLAUDE_CODE,
        sessionId = "test-session-id",
        model = "test-model",
    )
    return TmuxAgentSession(tmuxSession = tmuxSession, resumableAgentSessionId = resumableId)
}

private fun createTestSessionEntry(
    tmuxAgentSession: TmuxAgentSession,
): SessionEntry = SessionEntry(
    tmuxAgentSession = tmuxAgentSession,
    partName = "test-part",
    subPartName = "test-sub-part",
    subPartIndex = 0,
    signalDeferred = CompletableDeferred<AgentSignal>(),
    lastActivityTimestamp = AtomicReference(Instant.now()),
    pendingPayloadAck = AtomicReference(null),
    questionQueue = ConcurrentLinkedQueue(),
)
