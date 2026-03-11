package com.glassthought.chainsaw.core.agent.data

import com.glassthought.chainsaw.core.agent.sessionresolver.AgentSessionIdResolver
import com.glassthought.chainsaw.core.agent.starter.AgentStarter

/**
 * Pairs an [com.glassthought.chainsaw.core.agent.starter.AgentStarter] (builds the launch command) with an [com.glassthought.chainsaw.core.agent.sessionresolver.AgentSessionIdResolver]
 * (resolves the session identity via GUID handshake).
 *
 * Created by [com.glassthought.chainsaw.core.agent.AgentStarterBundleFactory] for a specific agent type and request.
 */
data class AgentStarterBundle(
  val starter: AgentStarter,
  val sessionIdResolver: AgentSessionIdResolver,
)