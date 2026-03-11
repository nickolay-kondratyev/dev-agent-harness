package com.glassthought.ticketShepherd.core.agent

import com.glassthought.ticketShepherd.core.agent.data.StartAgentRequest
import com.glassthought.ticketShepherd.core.data.AgentType

/**
 * Selects the [AgentType] to use for a given [StartAgentRequest].
 *
 * Decouples agent type selection from the spawn use case, allowing
 * future extension to multi-agent-type workflows without modifying
 * the orchestrator.
 */
interface AgentTypeChooser {
    fun choose(request: StartAgentRequest): AgentType
}

/**
 * V1 implementation: always selects [AgentType.CLAUDE_CODE].
 *
 * All workflow phases use Claude Code in the initial version.
 */
class DefaultAgentTypeChooser : AgentTypeChooser {
    override fun choose(request: StartAgentRequest): AgentType = AgentType.CLAUDE_CODE
}
