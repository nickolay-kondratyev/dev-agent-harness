package com.glassthought.shepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.asgard.core.out.OutFactory
import com.asgard.core.out.time
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunnerImpl
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.shepherd.core.agent.tmux.TmuxCommunicatorImpl
import com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager
import com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.adapter.GlmConfig
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.Constants
import java.nio.file.Path

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

/**
 * @param envVarReader Function to read environment variables. Default: [System.getenv].
 *   Injectable for unit testing without depending on real env vars.
 * @param fileReader Function to read file content as a string. Default: reads via [java.nio.file.Path.toFile].
 *   Injectable for unit testing without touching the real filesystem.
 * @param processRunnerFactory Factory to create [ProcessRunner]. Default: [ProcessRunner.standard].
 *   Injectable for unit testing without spawning real processes.
 */
class ContextInitializerImpl(
  private val envVarReader: (String) -> String? = System::getenv,
  private val fileReader: (Path) -> String = { it.toFile().readText() },
  private val processRunnerFactory: (OutFactory) -> ProcessRunner = { ProcessRunner.standard(it) },
) : ContextInitializer {

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
    val zaiApiKey = readZaiApiKey()

    val commandRunner = TmuxCommandRunner()
    val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
    val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

    val tmuxInfra = TmuxInfra(
      commandRunner = commandRunner,
      communicator = communicator,
      sessionManager = sessionManager,
    )

    // GLM config is wired so that spawned Claude Code agents can be redirected to GLM (Z.AI).
    // See ref.ap.8BYTb6vcyAzpWavQguBrb.E for config details.
    val glmConfig = GlmConfig.standard(authToken = zaiApiKey)

    val claudeCodeInfra = ClaudeCodeInfra(
      agentTypeAdapter = ClaudeCodeAdapter.create(
        claudeProjectsDir = Constants.CLAUDE_CODE.defaultProjectsDir(),
        outFactory = outFactory,
        glmConfig = glmConfig,
      ),
    )

    val infra = Infra(
      outFactory = outFactory,
      tmux = tmuxInfra,
      claudeCode = claudeCodeInfra,
    )

    val nonInteractiveAgentRunner = createNonInteractiveAgentRunner(outFactory, zaiApiKey)

    return ShepherdContext(
      infra = infra,
      nonInteractiveAgentRunner = nonInteractiveAgentRunner,
    )
  }

  private fun readZaiApiKey(): String {
    val myEnv = envVarReader(Constants.REQUIRED_ENV_VARS.MY_ENV)
      ?: error("${Constants.REQUIRED_ENV_VARS.MY_ENV} env var is not set")

    val zaiApiKeyPath = Path.of(myEnv, ".secrets", "Z_AI_GLM_API_TOKEN")
    val zaiApiKey = try {
      fileReader(zaiApiKeyPath).trim()
    } catch (e: java.io.IOException) {
      throw IllegalStateException(
        "Failed to read ZAI API key from [$zaiApiKeyPath]. " +
          "Ensure the file exists and is readable.",
        e,
      )
    }

    check(zaiApiKey.isNotEmpty()) {
      "ZAI API key file at [$zaiApiKeyPath] is empty."
    }

    return zaiApiKey
  }

  private fun createNonInteractiveAgentRunner(
    outFactory: OutFactory,
    zaiApiKey: String,
  ): NonInteractiveAgentRunner {
    val processRunner = processRunnerFactory(outFactory)

    return NonInteractiveAgentRunnerImpl(
      processRunner = processRunner,
      outFactory = outFactory,
      zaiApiKey = zaiApiKey,
    )
  }
}
