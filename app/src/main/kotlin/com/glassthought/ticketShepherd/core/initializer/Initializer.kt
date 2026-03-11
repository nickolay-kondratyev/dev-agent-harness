package com.glassthought.ticketShepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.asgard.core.out.OutFactory
import com.asgard.core.out.time
import com.glassthought.ticketShepherd.core.Constants
import com.glassthought.ticketShepherd.core.agent.DefaultAgentTypeChooser
import com.glassthought.ticketShepherd.core.useCase.SpawnTmuxAgentSessionUseCase
import com.glassthought.ticketShepherd.core.agent.impl.ClaudeCodeAgentStarterBundleFactory
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.DirectBudgetHighLLM
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.DirectQuickCheapLLM
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.glm.GLMHighestTierApi
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.glm.GLMQuickCheapApi
import com.glassthought.ticketShepherd.core.initializer.data.Environment
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxCommunicatorImpl
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxSessionManager
import com.glassthought.ticketShepherd.core.agent.tmux.util.TmuxCommandRunner
import okhttp3.OkHttpClient
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Groups tmux-related dependencies.
 */
data class TmuxInfra(
    val commandRunner: TmuxCommandRunner,
    val communicator: TmuxCommunicator,
    val sessionManager: TmuxSessionManager,
)

/**
 * Groups direct LLM API dependencies.
 *
 * [httpClient] is `internal` because it is only needed for resource cleanup in [ShepherdContext.close].
 * External consumers should use [quickCheap] or [budgetHigh] for API calls.
 */
data class DirectLlmInfra(
    val quickCheap: DirectQuickCheapLLM,
    val budgetHigh: DirectBudgetHighLLM,
    internal val httpClient: OkHttpClient,
)

/**
 * Top-level infrastructure grouping — all shared services and IO adapters.
 */
data class Infra(
    val outFactory: OutFactory,
    val tmux: TmuxInfra,
    val directLlm: DirectLlmInfra,
)

/**
 * Groups application use cases.
 */
data class UseCases(
    val spawnTmuxAgentSession: SpawnTmuxAgentSessionUseCase,
)

/**
 * Encapsulates all application-level dependencies created during initialization.
 *
 * Dependencies are organized into logical groups:
 * - [infra] — shared services and IO adapters (tmux, LLM, logging)
 * - [useCases] — application-level orchestration use cases
 *
 * Implements [AsgardCloseable] to ensure proper cleanup of all held resources.
 * Use via `.use{}` at the call site to guarantee shutdown even on exceptions.
 */
@AnchorPoint("ap.TkpljsXvwC6JaAVnIq02He98.E")
class ShepherdContext(
    val infra: Infra,
    val useCases: UseCases,
) : AsgardCloseable {

    override suspend fun close() {
        // Shut down OkHttpClient connection and thread pools to prevent resource leaks
        // in long-running server usage. Order matters: dispatcher first, then connections.
        infra.directLlm.httpClient.dispatcher.executorService.shutdown()
        infra.directLlm.httpClient.connectionPool.evictAll()
        infra.outFactory.close()
    }
}

/**
 * Root of all dependency wiring.
 *
 * Single public method [initialize] creates and connects all application-level
 * dependencies. AppMain.kt delegates to this interface rather than constructing
 * dependencies directly.
 */
interface Initializer {
    /**
     * @param outFactory Structured logging factory.
     * @param environment Runtime environment (test vs production).
     * @param systemPromptFilePath Absolute path to a system prompt file for the agent CLI, or null for default behavior.
     * @param claudeProjectsDir Directory where Claude stores session JSONL files.
     * @param httpClient Custom [OkHttpClient] to use for LLM API calls, or null to create a default one.
     *   Primarily useful for tests that need to verify resource cleanup behavior.
     */
    suspend fun initialize(
        outFactory: OutFactory,
        environment: Environment = Environment.production(),
        systemPromptFilePath: String? = null,
        claudeProjectsDir: Path = Path.of(System.getProperty("user.home"), ".claude", "projects"),
        httpClient: OkHttpClient? = null,
    ): ShepherdContext

    companion object {
        fun standard(): Initializer = InitializerImpl()
    }
}

class InitializerImpl : Initializer {

    override suspend fun initialize(
        outFactory: OutFactory,
        environment: Environment,
        systemPromptFilePath: String?,
        claudeProjectsDir: Path,
        httpClient: OkHttpClient?,
    ): ShepherdContext {
        val out = outFactory.getOutForClass(InitializerImpl::class)

        return out.time(
            { initializeImpl(outFactory, environment, systemPromptFilePath, claudeProjectsDir, httpClient) },
            "initializer.initialize",
        )
    }

    private fun initializeImpl(
        outFactory: OutFactory,
        environment: Environment,
        systemPromptFilePath: String?,
        claudeProjectsDir: Path,
        httpClient: OkHttpClient?,
    ): ShepherdContext {
        // TODO(ap.ifrXkqXjkvAajrA4QCy7V.E): use environment.isTest to swap external services for test doubles
        val commandRunner = TmuxCommandRunner()
        val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
        val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

        val tmuxInfra = TmuxInfra(
            commandRunner = commandRunner,
            communicator = communicator,
            sessionManager = sessionManager,
        )

        val httpClient = httpClient ?: OkHttpClient.Builder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val directLlmInfra = DirectLlmInfra(
            quickCheap = createGLMQuickCheapLLM(outFactory, httpClient),
            budgetHigh = createGLMBudgetHighLLM(outFactory, httpClient),
            httpClient = httpClient,
        )

        val infra = Infra(
            outFactory = outFactory,
            tmux = tmuxInfra,
            directLlm = directLlmInfra,
        )

        val bundleFactory = ClaudeCodeAgentStarterBundleFactory(
            environment = environment,
            systemPromptFilePath = systemPromptFilePath,
            claudeProjectsDir = claudeProjectsDir,
            outFactory = outFactory,
        )

        val agentTypeChooser = DefaultAgentTypeChooser()

        val spawnTmuxAgentSession = SpawnTmuxAgentSessionUseCase(
            agentTypeChooser = agentTypeChooser,
            bundleFactory = bundleFactory,
            tmuxSessionManager = sessionManager,
            outFactory = outFactory,
        )

        val useCases = UseCases(
            spawnTmuxAgentSession = spawnTmuxAgentSession,
        )

        return ShepherdContext(
            infra = infra,
            useCases = useCases,
        )
    }

    private fun createGLMQuickCheapLLM(outFactory: OutFactory, httpClient: OkHttpClient): DirectQuickCheapLLM =
        GLMQuickCheapApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = Constants.DIRECT_LLM_API_MODEL_NAME.GLM_QUICK_CHEAP,
            maxTokens = Constants.resolveMaxTokens(),
            apiEndpoint = Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT,
            apiToken = resolveApiToken(),
        )

    private fun createGLMBudgetHighLLM(outFactory: OutFactory, httpClient: OkHttpClient): DirectBudgetHighLLM =
        GLMHighestTierApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = Constants.DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER,
            maxTokens = Constants.resolveMaxTokens(),
            apiEndpoint = Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT,
            apiToken = resolveApiToken(),
        )

    private fun resolveApiToken(): String =
        System.getenv(Constants.Z_AI_API.API_TOKEN_ENV_VAR)
            ?: throw IllegalStateException(
                "Required environment variable [${Constants.Z_AI_API.API_TOKEN_ENV_VAR}] is not set"
            )

    companion object {
        /** LLM API calls routinely take 10-30 seconds; default OkHttp 10s would cause failures. */
        private const val READ_TIMEOUT_SECONDS = 60L
    }
}
