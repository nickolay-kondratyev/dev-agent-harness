package com.glassthought.ticketShepherd.core.agent.data

import com.glassthought.ticketShepherd.core.agent.sessionresolver.AgentSessionIdResolver
import com.glassthought.ticketShepherd.core.agent.starter.AgentStarter

/**
 * Pairs an [com.glassthought.ticketShepherd.core.agent.starter.AgentStarter] (builds the launch command) with an [com.glassthought.ticketShepherd.core.agent.sessionresolver.AgentSessionIdResolver]
 * (resolves the session identity via GUID handshake).
 *
 * Created by [com.glassthought.ticketShepherd.core.agent.AgentStarterBundleFactory] for a specific agent type and request.
 */
data class AgentStarterBundle(
  val starter: AgentStarter,
  val sessionIdResolver: AgentSessionIdResolver,
)