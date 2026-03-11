package com.glassthought.chainsaw.core.agent

import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.data.AgentType

/**
 * Creates [AgentStarterBundle] instances for a given agent type and request.
 *
 * Takes the full [StartAgentRequest] (not just phaseType) because the factory
 * needs [StartAgentRequest.workingDir] to construct the [com.glassthought.chainsaw.core.agent.starter.AgentStarter].
 */
interface AgentStarterBundleFactory {
    fun create(agentType: AgentType, request: StartAgentRequest): AgentStarterBundle
}
