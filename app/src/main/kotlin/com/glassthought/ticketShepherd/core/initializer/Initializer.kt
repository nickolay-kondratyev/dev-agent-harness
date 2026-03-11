package com.glassthought.ticketShepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.asgard.core.out.OutFactory
import com.asgard.core.out.time
import com.glassthought.ticketShepherd.core.Constants
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.DirectLLM
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.glm.GLMHighestTierApi
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxCommunicator
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxCommunicatorImpl
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxSessionManager
import com.glassthought.ticketShepherd.core.agent.tmux.util.TmuxCommandRunner
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
 * [httpClient] is `internal` because it is only needed for resource cleanup in [ShepherdContext.close].
 * External consumers should use [glmDirectLLM] for API calls.
 */
data class DirectLlmInfra(
    val glmDirectLLM: DirectLLM,
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
 * Encapsulates all application-level dependencies created during initialization.
 *
 * Dependencies are organized into logical groups:
 * - [infra] — shared services and IO adapters (tmux, LLM, logging)
 *
 * Implements [AsgardCloseable] to ensure proper cleanup of all held resources.
 * Use via `.use{}` at the call site to guarantee shutdown even on exceptions.
 */
@AnchorPoint("ap.TkpljsXvwC6JaAVnIq02He98.E")
class ShepherdContext(
    val infra: Infra,
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

        val glmDirectLLM = createGLMDirectLLM(outFactory, httpClient)

        val directLlmInfra = DirectLlmInfra(
            glmDirectLLM = glmDirectLLM,
            httpClient = httpClient,
        )

        val infra = Infra(
            outFactory = outFactory,
            tmux = tmuxInfra,
            directLlm = directLlmInfra,
        )

        return ShepherdContext(
            infra = infra,
        )
    }

    private fun createGLMDirectLLM(outFactory: OutFactory, httpClient: OkHttpClient): DirectLLM {
        val config = Constants.getConfigurationObject()

        val apiToken = System.getenv(Constants.Z_AI_API.API_TOKEN_ENV_VAR)
            ?: throw IllegalStateException(
                "Required environment variable [${Constants.Z_AI_API.API_TOKEN_ENV_VAR}] is not set"
            )

        return GLMHighestTierApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = config.zAiGlmConfig.modelName,
            maxTokens = config.zAiGlmConfig.maxTokens,
            apiEndpoint = Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT,
            apiToken = apiToken,
        )
    }

    companion object {
        /** LLM API calls routinely take 10-30 seconds; default OkHttp 10s would cause failures. */
        private const val READ_TIMEOUT_SECONDS = 60L
    }
}
