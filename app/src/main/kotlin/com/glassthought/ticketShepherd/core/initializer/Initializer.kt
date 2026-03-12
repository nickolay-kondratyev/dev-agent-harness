package com.glassthought.ticketShepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.OutFactory
import com.asgard.core.out.time
import com.glassthought.ticketShepherd.core.Constants
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.DirectBudgetHighLLM
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.DirectQuickCheapLLM
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.glm.GLMHighestTierApi
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.glm.GLMQuickCheapApi
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxCommunicatorImpl
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxSessionManager
import com.glassthought.ticketShepherd.core.agent.tmux.util.TmuxCommandRunner
import com.glassthought.ticketShepherd.core.agent.sessionresolver.impl.ClaudeCodeAgentSessionIdResolver
import com.glassthought.ticketShepherd.core.initializer.data.ShepherdContext
import okhttp3.OkHttpClient
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
 * [httpClient] is `internal` because it is only needed for resource cleanup in [com.glassthought.ticketShepherd.core.initializer.data.ShepherdContext.close].
 * External consumers should use [quickCheap] or [budgetHigh] for API calls.
 */
data class DirectLlmInfra(
    val quickCheap: DirectQuickCheapLLM,
    val budgetHigh: DirectBudgetHighLLM,
    internal val httpClient: OkHttpClient,
)

/**
 * Groups Claude Code agent dependencies.
 */
data class ClaudeCodeInfra(
    val sessionIdResolver: ClaudeCodeAgentSessionIdResolver,
)

/**
 * Top-level infrastructure grouping — all shared services and IO adapters.
 */
data class Infra(
    val outFactory: OutFactory,
    val tmux: TmuxInfra,
    val directLlm: DirectLlmInfra,
    val claudeCode: ClaudeCodeInfra,
)

/**
 * Root of all dependency wiring.
 *
 * Single public method [initialize] creates and connects all application-level
 * dependencies. AppMain.kt delegates to this interface rather than constructing
 * dependencies directly.
 */
@AnchorPoint("ap.9zump9YISPSIcdnxEXZZX.E")
interface Initializer {
    /**
     * @param outFactory Structured logging factory.
     * @param httpClient Custom [OkHttpClient] to use for LLM API calls, or null to create a default one.
     *   Primarily useful for tests that need to verify resource cleanup behavior.
     */
    suspend fun initialize(
        outFactory: OutFactory,
        httpClient: OkHttpClient? = null,
    ): ShepherdContext

    companion object {
        fun standard(): Initializer = InitializerImpl()
    }
}

class InitializerImpl : Initializer {

    override suspend fun initialize(
        outFactory: OutFactory,
        httpClient: OkHttpClient?,
    ): ShepherdContext {
        val out = outFactory.getOutForClass(InitializerImpl::class)

        return out.time(
            { initializeImpl(outFactory, httpClient) },
            "initializer.initialize",
        )
    }

    private fun initializeImpl(
        outFactory: OutFactory,
        httpClient: OkHttpClient?,
    ): ShepherdContext {
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

        val claudeCodeInfra = ClaudeCodeInfra(
            sessionIdResolver = ClaudeCodeAgentSessionIdResolver(
                claudeProjectsDir = Constants.CLAUDE_CODE.defaultProjectsDir(),
                outFactory = outFactory,
                model = Constants.CLAUDE_CODE.DEFAULT_MODEL,
            ),
        )

        val infra = Infra(
            outFactory = outFactory,
            tmux = tmuxInfra,
            directLlm = directLlmInfra,
            claudeCode = claudeCodeInfra,
        )

        return ShepherdContext(
            infra = infra,
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
