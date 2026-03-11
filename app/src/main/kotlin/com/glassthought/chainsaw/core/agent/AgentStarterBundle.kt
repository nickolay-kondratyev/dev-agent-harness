package com.glassthought.chainsaw.core.agent

import com.glassthought.chainsaw.core.agent.starter.AgentStarter
import com.glassthought.chainsaw.core.sessionresolver.AgentSessionIdResolver

/**
 * Pairs an [AgentStarter] (builds the launch command) with an [AgentSessionIdResolver]
 * (resolves the session identity via GUID handshake).
 *
 * Created by [AgentStarterBundleFactory] for a specific agent type and request.
 */
data class AgentStarterBundle(
    val starter: AgentStarter,
    val sessionIdResolver: AgentSessionIdResolver,
)
