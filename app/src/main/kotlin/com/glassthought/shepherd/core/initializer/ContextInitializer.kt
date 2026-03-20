package com.glassthought.shepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.asgard.core.out.OutFactory
import com.asgard.core.out.time
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner
import com.glassthought.shepherd.core.creator.ProcessRunnerFactory
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
    /** Production wiring — GLM is NOT enabled; agents use the real Anthropic API. */
    fun standard(): ContextInitializer = ContextInitializerImpl()

    /**
     * Integration test wiring — GLM IS enabled; agents are redirected to GLM (Z.AI).
     * See `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md` for rationale.
     *
     * WHY sentinel port/scripts: Integration tests create their own server with a dynamic port
     * and override these values via [ServerPortInjectingAdapter]. The adapter's native values
     * are overridden at command-build time, so these sentinels are never exposed to agents.
     */
    fun forIntegTest(): ContextInitializer = ContextInitializerImpl(
      glmEnabled = true,
      // WHY-NOT reading from env var: integration tests pick a dynamic port after context init,
      // and inject it via ServerPortInjectingAdapter which overrides the entire command.
      serverPortOverride = INTEG_TEST_SENTINEL_PORT,
      callbackScriptsDirOverride = INTEG_TEST_SENTINEL_SCRIPTS_DIR,
    )

    /**
     * Sentinel values for integration test wiring. These are never actually used because
     * [ServerPortInjectingAdapter] replaces them in the generated command. They exist only
     * to avoid requiring `TICKET_SHEPHERD_SERVER_PORT` env var during integ test initialization.
     */
    private const val INTEG_TEST_SENTINEL_PORT = 0
    private const val INTEG_TEST_SENTINEL_SCRIPTS_DIR = "/unused-integ-test-sentinel"
  }
}

/**
 * @param envVarReader Function to read environment variables. Default: [System.getenv].
 *   Injectable for unit testing without depending on real env vars.
 * @param fileReader Function to read file content as a string. Default: reads via [java.nio.file.Path.toFile].
 *   Injectable for unit testing without touching the real filesystem.
 * @param processRunnerFactory Factory to create [ProcessRunner]. Default: [ProcessRunner.standard].
 *   Injectable for unit testing without spawning real processes.
 * @param serverPortOverride When non-null, uses this port directly instead of reading from env var.
 *   Used by integration tests where the port is dynamically assigned and injected via
 *   [com.glassthought.shepherd.integtest.ServerPortInjectingAdapter].
 * @param callbackScriptsDirOverride When non-null, uses this directory directly instead of
 *   extracting scripts from classpath resources.
 */
class ContextInitializerImpl(
  private val envVarReader: (String) -> String? = System::getenv,
  private val fileReader: (Path) -> String = { it.toFile().readText() },
  private val processRunnerFactory: ProcessRunnerFactory = ProcessRunnerFactory { ProcessRunner.standard(it) },
  private val glmEnabled: Boolean = false,
  private val serverPortOverride: Int? = null,
  private val callbackScriptsDirOverride: String? = null,
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

    // GLM config redirects spawned Claude Code agents to GLM (Z.AI) instead of real Anthropic API.
    // Only enabled for integration tests — production agents use the real Anthropic API.
    // See ref.ap.8BYTb6vcyAzpWavQguBrb.E for config details.
    val glmConfig = if (glmEnabled) GlmConfig.standard(authToken = zaiApiKey) else null

    val serverPort = readServerPort()
    val callbackScriptsDir = resolveCallbackScriptsDir()

    val claudeCodeInfra = ClaudeCodeInfra(
      agentTypeAdapter = ClaudeCodeAdapter.create(
        claudeProjectsDir = Constants.CLAUDE_CODE.defaultProjectsDir(),
        outFactory = outFactory,
        serverPort = serverPort,
        callbackScriptsDir = callbackScriptsDir,
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

  /**
   * Returns the server port, either from [serverPortOverride] or from the
   * [Constants.AGENT_COMM.SERVER_PORT_ENV_VAR] env var.
   *
   * WHY reading it again here (also read by [ShepherdInitializer] for server binding):
   * The adapter needs the port to export it into each spawned agent's tmux session
   * so callback scripts know which port to POST to.
   */
  private fun readServerPort(): Int {
    if (serverPortOverride != null) return serverPortOverride

    val envVar = Constants.AGENT_COMM.SERVER_PORT_ENV_VAR
    val portStr = envVarReader(envVar)
      ?: error("Environment variable [$envVar] is not set. " +
        "It must specify the port for the embedded HTTP server.")
    return portStr.toIntOrNull()
      ?: error("Environment variable [$envVar] has invalid value [$portStr]. " +
        "It must be a valid port number.")
  }

  /**
   * Returns the callback scripts directory, either from [callbackScriptsDirOverride] or by
   * extracting `callback_shepherd.signal.sh` from classpath resources to a temp directory.
   *
   * The scripts live at `src/main/resources/scripts/` relative to the `app` module.
   * At runtime (when running from the Gradle-built distribution), they are on the classpath.
   */
  private fun resolveCallbackScriptsDir(): String {
    if (callbackScriptsDirOverride != null) return callbackScriptsDirOverride

    val scriptName = "callback_shepherd.signal.sh"
    val resourcePath = "/scripts/$scriptName"

    val inputStream = javaClass.getResourceAsStream(resourcePath)
      ?: error("Callback script not found on classpath at [$resourcePath]. " +
        "Ensure the resources are included in the build.")

    val tempDir = java.nio.file.Files.createTempDirectory("shepherd-callback-scripts")
    val targetFile = tempDir.resolve(scriptName).toFile()

    inputStream.use { input ->
      targetFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }

    targetFile.setExecutable(true)

    return tempDir.toAbsolutePath().toString()
  }

  private fun createNonInteractiveAgentRunner(
    outFactory: OutFactory,
    zaiApiKey: String,
  ): NonInteractiveAgentRunner {
    val processRunner = processRunnerFactory.create(outFactory)

    return NonInteractiveAgentRunnerImpl(
      processRunner = processRunner,
      outFactory = outFactory,
      zaiApiKey = zaiApiKey,
    )
  }
}
