package com.glassthought.chainsaw.core.wingman

import com.glassthought.chainsaw.core.data.AgentType

/**
 * Strongly-typed session identifier for a resumable agent session.
 *
 * Pairs the raw session ID string with the [com.glassthought.chainsaw.core.data.AgentType] that owns it, so callers
 * know how to resume the session without losing type information.
 *
 * Returned by [Wingman]/ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E after resolving a GUID handshake.
 */
data class ResumableAgentSessionId(
    val agentType: AgentType,
    val sessionId: String,
)
