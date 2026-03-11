package com.glassthought.chainsaw.core.agent.sessionresolver

import com.asgard.core.annotation.AnchorPoint
import java.util.UUID

/**
 * Strongly-typed GUID marker used for handshake between harness and agent.
 *
 * The harness generates a GUID and sends it to the agent as the first TMUX message.
 * This value class provides type safety to distinguish the handshake GUID from other strings.
 *
 * Always create instances via [generate] to ensure the required `handshake.` prefix is present.
 * The prefix makes GUIDs greppable in logs and distinguishable from agent session IDs.
 *
 * This is used by [AgentSessionIdResolver]/ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E to resolve the agent session ID from the GUID.
 * - captured in SpawnTmuxAgentSessionUseCase.md/ref.ap.hZdTRho3gQwgIXxoUtTqy.E
 */
@AnchorPoint("ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E")
@JvmInline
value class HandshakeGuid(val value: String) {
    override fun toString(): String = value

    companion object {
        private const val PREFIX = "handshake."

        /** Generates a new [HandshakeGuid] with the required `handshake.` prefix. */
        fun generate(): HandshakeGuid = HandshakeGuid("$PREFIX${UUID.randomUUID()}")
    }
}
