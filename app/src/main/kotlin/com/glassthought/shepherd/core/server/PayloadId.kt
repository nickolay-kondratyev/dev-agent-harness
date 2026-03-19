package com.glassthought.shepherd.core.server

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import java.util.concurrent.atomic.AtomicInteger

/**
 * Strongly-typed identifier for payloads sent from harness to agent.
 *
 * Format: `{handshakeGuid_short}-{sequenceN}` — e.g., `a1b2c3d4-3` for the 3rd payload in that session.
 * - `handshakeGuid_short` = first 8 characters of the HandshakeGuid UUID (excluding the `handshake.` prefix)
 * - `sequenceN` = per-session AtomicInteger counter starting at 1, incremented for each payload sent
 *
 * Design rationale:
 * - Counter increment vs. random string generation — no UUID/random library dependency
 * - Self-correlating: prefix identifies session, sequence identifies payload order
 * - Sequence numbers reveal gaps for debugging
 * - Deterministic IDs enable easier test assertions
 */
@AnchorPoint("ap.3Wug9Q5QRHDSOvMq0FB3d.E")
@JvmInline
value class PayloadId(val value: String) {
    override fun toString(): String = value

    companion object {
        private const val SHORT_GUID_LENGTH = 8

        /**
         * Generates a new PayloadId from the HandshakeGuid and a counter.
         * The counter should be an AtomicInteger owned by the session, starting at 1.
         */
        fun generate(handshakeGuid: HandshakeGuid, counter: AtomicInteger): PayloadId {
            val shortGuid = handshakeGuid.value.removePrefix("handshake.").take(SHORT_GUID_LENGTH)
            val seq = counter.getAndIncrement()
            return PayloadId("$shortGuid-$seq")
        }
    }
}
