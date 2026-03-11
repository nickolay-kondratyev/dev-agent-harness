package com.glassthought.ticketShepherd.core.agent

import com.glassthought.ticketShepherd.core.agent.data.AgentStarterBundle
import com.glassthought.ticketShepherd.core.agent.data.StartAgentRequest
import com.glassthought.ticketShepherd.core.data.AgentType

/**
 * Creates [com.glassthought.ticketShepherd.core.agent.data.AgentStarterBundle] instances for a given agent type and request.
 *
 * Takes the full [StartAgentRequest] (not just phaseType) because the factory
 * needs [StartAgentRequest.workingDir] to construct the [com.glassthought.ticketShepherd.core.agent.starter.AgentStarter].
 */
interface AgentStarterBundleFactory {
    fun create(agentType: AgentType, request: StartAgentRequest): AgentStarterBundle
}
