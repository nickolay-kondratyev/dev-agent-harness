package com.glassthought.chainsaw.core.data

/**
 * Identifies the type of agent whose session can be resumed.
 *
 * Used in [com.glassthought.chainsaw.core.agent.sessionresolver.ResumableAgentSessionId] to provide context on how to resume a session.
 */
enum class AgentType {
    CLAUDE_CODE,
    PI,
}