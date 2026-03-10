package com.glassthought.chainsaw.core.wingman

/**
 * Identifies the type of agent whose session can be resumed.
 *
 * Used in [ResumableAgentSessionId] to provide context on how to resume a session.
 */
enum class AgentType {
    CLAUDE_CODE,
    PI,
}
