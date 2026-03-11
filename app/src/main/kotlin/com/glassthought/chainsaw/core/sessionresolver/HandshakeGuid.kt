package com.glassthought.chainsaw.core.sessionresolver

/**
 * Strongly-typed GUID marker used for handshake between harness and agent.
 *
 * The harness generates a GUID and sends it to the agent as the first TMUX message.
 * This value class provides type safety to distinguish the handshake GUID from other strings.
 *
 * This is used by [AgentSessionIdResolver]/ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E to resolve the agent session ID from the GUID.
 */
@JvmInline
value class HandshakeGuid(val value: String) {
    override fun toString(): String = value
}
