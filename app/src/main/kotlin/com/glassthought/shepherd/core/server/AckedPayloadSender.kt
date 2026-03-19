package com.glassthought.shepherd.core.server

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import com.glassthought.shepherd.core.session.SessionEntry
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sole gateway for all harness→agent `send-keys` communication.
 *
 * Wraps payload content in the Payload Delivery ACK XML, sends via TMUX `send-keys`,
 * and awaits ACK from the agent. Retries on timeout per the retry policy.
 *
 * See ref.ap.r0us6iYsIRzrqHA5MVO0Q.E for the Payload Delivery ACK Protocol.
 *
 * Callers include: work instruction delivery (Phase 2), Q&A answer delivery,
 * iteration feedback, PUBLIC.md re-instruction, and health pings.
 */
@AnchorPoint("ap.m6X7We58LwUAu4khybhtZ.E")
interface AckedPayloadSender {
    /**
     * Wrap [payloadContent] in the Payload Delivery ACK XML, send via TMUX send-keys,
     * and await ACK. Retries per the retry policy (3 attempts, 3 min each).
     *
     * Returns on successful ACK. Throws [PayloadAckTimeoutException] on all-retries-exhausted
     * (caller handles as crash).
     */
    suspend fun sendAndAwaitAck(
        tmuxSession: TmuxAgentSession,
        sessionEntry: SessionEntry,
        payloadContent: String,
    )
}

/**
 * Default implementation of [AckedPayloadSender].
 *
 * Generates a [PayloadId] using the session's [HandshakeGuid] and the provided
 * [payloadCounter], wraps payload in XML per the spec, sets [SessionEntry.pendingPayloadAck]
 * before sending, sends via TMUX `send-keys`, and polls until ACK arrives or timeout.
 *
 * @param payloadCounter Per-session [AtomicInteger] counter for [PayloadId] generation.
 *   Starts at 1, incremented for each payload sent. Owned by the caller (typically
 *   per-session in the facade).
 * @param ackTimeout Duration to wait for ACK before retrying. Default: 3 minutes.
 * @param pollInterval Duration between polls of the pendingPayloadAck field. Default: 100ms.
 * @param maxAttempts Maximum number of send attempts. Default: 3.
 */
class AckedPayloadSenderImpl(
    outFactory: OutFactory,
    private val payloadCounter: AtomicInteger,
    private val ackTimeout: Duration = ACK_TIMEOUT_DEFAULT,
    private val pollInterval: Duration = POLL_INTERVAL_DEFAULT,
    private val maxAttempts: Int = MAX_ATTEMPTS_DEFAULT,
) : AckedPayloadSender {

    private val out = outFactory.getOutForClass(AckedPayloadSenderImpl::class)

    override suspend fun sendAndAwaitAck(
        tmuxSession: TmuxAgentSession,
        sessionEntry: SessionEntry,
        payloadContent: String,
    ) {
        val handshakeGuid = tmuxSession.resumableAgentSessionId.handshakeGuid
        val payloadId = PayloadId.generate(handshakeGuid, payloadCounter)
        val wrappedPayload = wrapPayload(payloadId, payloadContent)

        for (attempt in 1..maxAttempts) {
            out.info(
                "sending_acked_payload",
                Val(payloadId.value, ShepherdValType.PAYLOAD_ID),
                Val(attempt.toString(), ShepherdValType.ATTEMPT_NUMBER),
            )

            sessionEntry.pendingPayloadAck.set(payloadId)
            tmuxSession.tmuxSession.sendKeys(wrappedPayload)

            if (awaitAck(sessionEntry)) {
                out.info(
                    "payload_ack_received",
                    Val(payloadId.value, ShepherdValType.PAYLOAD_ID),
                )
                return
            }

            out.warn(
                "payload_ack_timeout",
                Val(payloadId.value, ShepherdValType.PAYLOAD_ID),
                Val(attempt.toString(), ShepherdValType.ATTEMPT_NUMBER),
                Val(maxAttempts.toString(), ShepherdValType.MAX_ATTEMPTS),
            )
        }

        throw PayloadAckTimeoutException(
            "All $maxAttempts attempts exhausted for payload [$payloadId] — agent alive but unable to process input"
        )
    }

    /**
     * Polls [SessionEntry.pendingPayloadAck] until it is cleared (null) or [ackTimeout] elapses.
     *
     * @return true if ACK received (field cleared), false if timed out.
     */
    private suspend fun awaitAck(sessionEntry: SessionEntry): Boolean {
        val deadline = System.nanoTime() + ackTimeout.inWholeNanoseconds

        while (System.nanoTime() < deadline) {
            if (sessionEntry.pendingPayloadAck.get() == null) {
                return true
            }
            delay(pollInterval)
        }

        return sessionEntry.pendingPayloadAck.get() == null
    }

    companion object {
        private val ACK_TIMEOUT_DEFAULT = 3.minutes
        private val POLL_INTERVAL_DEFAULT = 100.milliseconds
        private const val MAX_ATTEMPTS_DEFAULT = 3

        /**
         * Wraps [payloadContent] in the Payload Delivery ACK XML format.
         *
         * Visible for testing.
         */
        fun wrapPayload(payloadId: PayloadId, payloadContent: String): String {
            val tag = ProtocolVocabulary.PAYLOAD_ACK_TAG
            val script = ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT
            val signal = ProtocolVocabulary.Signal.ACK_PAYLOAD
            val ackCommand = "$script $signal $payloadId"

            return "<$tag payload_id=\"$payloadId\" MUST_ACK_BEFORE_PROCEEDING=\"$ackCommand\">\n" +
                "$payloadContent\n" +
                "</$tag>"
        }
    }
}
