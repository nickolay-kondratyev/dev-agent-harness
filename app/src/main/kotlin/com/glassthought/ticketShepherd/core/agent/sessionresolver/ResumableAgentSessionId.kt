package com.glassthought.ticketShepherd.core.agent.sessionresolver

import com.glassthought.ticketShepherd.core.data.AgentType

/**
 * Strongly-typed session identifier for a resumable agent session.
 *
 * Carries [handshakeGuid] alongside [agentType], [sessionId], and [model] so the identity
 * is self-contained for both routing (server maps [HandshakeGuid] → session) and persistence
 * (`current_state.json` `handshake_guid` field).
 *
 * Returned by [AgentSessionIdResolver]/ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E after resolving a GUID handshake.
 */
data class ResumableAgentSessionId(
    val handshakeGuid: HandshakeGuid,
    val agentType: AgentType,
    val sessionId: String,
    val model: String,
)
