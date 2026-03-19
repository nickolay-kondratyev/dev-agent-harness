package com.glassthought.shepherd.core.agent.sessionresolver

import com.glassthought.shepherd.core.data.AgentType

/**
 * Strongly-typed session identifier for a resumable agent session.
 *
 * Carries [handshakeGuid] alongside [agentType], [sessionId], and [model] so the identity
 * is self-contained for both routing (server maps [HandshakeGuid] → session) and persistence
 * (`current_state.json` `handshake_guid` field).
 *
 * The caller constructs this from the raw session ID returned by
 * [com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter.resolveSessionId]/ref.ap.hhP3gT9qK2mR8vNwX5dYa.E.
 */
data class ResumableAgentSessionId(
    val handshakeGuid: HandshakeGuid,
    val agentType: AgentType,
    val sessionId: String,
    val model: String,
)
