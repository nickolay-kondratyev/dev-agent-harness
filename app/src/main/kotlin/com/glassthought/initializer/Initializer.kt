package com.glassthought.initializer

import com.asgard.core.out.OutFactory
import com.asgard.core.out.impl.console.SimpleConsoleOutFactory
import com.glassthought.Constants
import com.glassthought.directLLMApi.DirectLLM
import com.glassthought.directLLMApi.glm.GLMHighestTierApi
import com.glassthought.tmux.TmuxCommunicator
import com.glassthought.tmux.TmuxCommunicatorImpl
import com.glassthought.tmux.TmuxSessionManager
import com.glassthought.tmux.util.TmuxCommandRunner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Root of all dependency wiring.
 *
 * Responsible for creating and connecting all application-level dependencies.
 * App.kt delegates to this class rather than constructing dependencies directly.
 */
class Initializer {

    /**
     * Encapsulates all application-level dependencies created during initialization.
     * Created by [Initializer.initialize] and must be used within an [OutFactory.use] block.
     */
    data class AppDependencies(
        val outFactory: OutFactory,
        val tmuxCommandRunner: TmuxCommandRunner,
        val tmuxCommunicator: TmuxCommunicator,
        val tmuxSessionManager: TmuxSessionManager,
    )

    /**
     * Creates all application dependencies.
     * The returned [AppDependencies.outFactory] must be closed by the caller via `.use{}`.
     */
    fun initialize(): AppDependencies {
        val outFactory = SimpleConsoleOutFactory.standard()

        val commandRunner = TmuxCommandRunner()
        val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
        val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

        return AppDependencies(
            outFactory = outFactory,
            tmuxCommandRunner = commandRunner,
            tmuxCommunicator = communicator,
            tmuxSessionManager = sessionManager,
        )
    }

    /**
     * Creates a [DirectLLM] instance configured for Z.AI GLM highest-tier model.
     *
     * Callers should reuse the returned [DirectLLM] instance rather than calling
     * this method repeatedly -- OkHttp recommends a single shared client for
     * connection pooling.
     *
     * @param outFactory The OutFactory for logging (must come from [AppDependencies]).
     * @throws IllegalStateException if [Constants.Z_AI_API.API_TOKEN_ENV_VAR] environment variable is not set.
     */
    fun createGLMDirectLLM(outFactory: OutFactory): DirectLLM {
        val config = Constants.getConfigurationObject()

        val apiToken = System.getenv(Constants.Z_AI_API.API_TOKEN_ENV_VAR)
            ?: throw IllegalStateException(
                "Required environment variable [${Constants.Z_AI_API.API_TOKEN_ENV_VAR}] is not set"
            )

        // OkHttpClient lifecycle is tied to process lifetime, which is acceptable
        // for a CLI application. For long-running processes, add proper shutdown.
        val httpClient = OkHttpClient.Builder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return GLMHighestTierApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = config.zAiGlmConfig.highestTier,
            apiEndpoint = Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT,
            apiToken = apiToken,
        )
    }

    companion object {
        /** LLM API calls routinely take 10-30 seconds; default OkHttp 10s would cause failures. */
        private const val READ_TIMEOUT_SECONDS = 60L
    }
}
