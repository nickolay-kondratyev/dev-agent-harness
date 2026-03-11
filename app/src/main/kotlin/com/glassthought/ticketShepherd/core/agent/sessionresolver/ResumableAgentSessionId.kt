package com.glassthought.ticketShepherd.core.agent.sessionresolver

import com.glassthought.ticketShepherd.core.data.AgentType

/**
 * Strongly-typed session identifier for a resumable agent session.
 *
 * Pairs the raw session ID string with the [AgentType] and [model] that owns it, so callers
 * know how to resume the session (e.g. `claude --resume <sessionId> --model <model>`) without
 * losing type information.
 *
 * Returned by [AgentSessionIdResolver]/ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E after resolving a GUID handshake.
 */
data class ResumableAgentSessionId(
    val agentType: AgentType,
    val sessionId: String,
    val model: String,
)
