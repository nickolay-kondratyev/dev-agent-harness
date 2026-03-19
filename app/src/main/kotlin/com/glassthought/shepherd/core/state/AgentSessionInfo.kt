package com.glassthought.shepherd.core.state

/**
 * Identifies a single agent session by its unique ID.
 *
 * @property id The session identifier (UUID format).
 */
data class AgentSessionInfo(
    val id: String,
)
