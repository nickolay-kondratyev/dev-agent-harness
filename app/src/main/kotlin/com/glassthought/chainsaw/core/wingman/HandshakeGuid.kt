package com.glassthought.chainsaw.core.wingman

/**
 * Strongly-typed GUID marker used for handshake between harness and agent.
 *
 * The harness generates a GUID and sends it to the agent as the first TMUX message.
 * This value class provides type safety to distinguish the handshake GUID from other strings.
 */
@JvmInline
value class HandshakeGuid(val value: String) {
    override fun toString(): String = value
}
