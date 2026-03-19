package com.glassthought.shepherd.core.state

/**
 * Records a single agent session spawned for a sub-part.
 *
 * Appended to [SubPart.sessionIds] each time the harness spawns an agent.
 *
 * @property handshakeGuid The handshake GUID used to correlate the spawn request with the agent callback.
 * @property agentSession The agent session identifier.
 * @property agentType The type of agent spawned (e.g., "ClaudeCode").
 * @property model The model used by the agent (e.g., "sonnet").
 * @property timestamp ISO-8601 timestamp of when the session was spawned.
 */
data class SessionRecord(
    val handshakeGuid: String,
    val agentSession: AgentSessionInfo,
    val agentType: String,
    val model: String,
    val timestamp: String,
)
