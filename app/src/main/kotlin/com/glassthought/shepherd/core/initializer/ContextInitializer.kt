package com.glassthought.shepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.asgard.core.out.OutFactory
import com.asgard.core.out.time
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicatorImpl
import com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager
import com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.Constants

/**
 * Groups tmux-related dependencies.
 */
data class TmuxInfra(
  val commandRunner: TmuxCommandRunner,
  val communicator: TmuxCommunicator,
  val sessionManager: TmuxSessionManager,
)

/**
 * Groups Claude Code agent dependencies.
 */
data class ClaudeCodeInfra(
  val agentTypeAdapter: AgentTypeAdapter,
)

/**
 * Top-level infrastructure grouping — all shared services and IO adapters.
 */
data class Infra(
  val outFactory: OutFactory,
  val tmux: TmuxInfra,
  val claudeCode: ClaudeCodeInfra,
) : AsgardCloseable {
  override suspend fun close() {
    // Out factory should be the last to close
    this.outFactory.close()
  }
}

/**
 * Wires shared infrastructure dependencies (tmux, LLM, logging) into a [ShepherdContext].
 *
 * This is the **context-only** initializer — it builds the infrastructure layer that
 * outlives any single ticket. The top-level `Initializer` (not yet implemented) will
 * orchestrate this alongside server startup and ticket-scoped wiring.
 *
 * Single public method [initialize] creates and connects all infrastructure-level
 * dependencies.
 */
@AnchorPoint("ap.9zump9YISPSIcdnxEXZZX.E")
fun interface ContextInitializer {
  /**
   * @param outFactory Structured logging factory.
   */
  suspend fun initialize(
    outFactory: OutFactory,
  ): ShepherdContext

  companion object {
    fun standard(): ContextInitializer = ContextInitializerImpl()
  }
}

class ContextInitializerImpl : ContextInitializer {

  override suspend fun initialize(
    outFactory: OutFactory,
  ): ShepherdContext {
    val out = outFactory.getOutForClass(ContextInitializerImpl::class)

    return out.time(
      { initializeImpl(outFactory) },
      "context_initializer.initialize",
    )
  }

  private fun initializeImpl(
    outFactory: OutFactory,
  ): ShepherdContext {
    val commandRunner = TmuxCommandRunner()
    val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
    val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

    val tmuxInfra = TmuxInfra(
      commandRunner = commandRunner,
      communicator = communicator,
      sessionManager = sessionManager,
    )

    val claudeCodeInfra = ClaudeCodeInfra(
      agentTypeAdapter = ClaudeCodeAdapter.create(
        claudeProjectsDir = Constants.CLAUDE_CODE.defaultProjectsDir(),
        outFactory = outFactory,
      ),
    )

    val infra = Infra(
      outFactory = outFactory,
      tmux = tmuxInfra,
      claudeCode = claudeCodeInfra,
    )

    return ShepherdContext(
      infra = infra,
    )
  }
}
