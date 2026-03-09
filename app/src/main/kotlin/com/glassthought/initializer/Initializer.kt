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
 * Encapsulates all application-level dependencies created during initialization.
 * The [outFactory] must be closed by the caller via `.use{}`.
 */
data class AppDependencies(
    val outFactory: OutFactory,
    val tmuxCommandRunner: TmuxCommandRunner,
    val tmuxCommunicator: TmuxCommunicator,
    val tmuxSessionManager: TmuxSessionManager,
    val glmDirectLLM: DirectLLM,
)

/**
 * Root of all dependency wiring.
 *
 * Single public method [initialize] creates and connects all application-level
 * dependencies. App.kt delegates to this interface rather than constructing
 * dependencies directly.
 */
interface Initializer {
    fun initialize(): AppDependencies

    companion object{
        fun standard(): Initializer = InitializerImpl()
    }
}

class InitializerImpl : Initializer {

    override fun initialize(): AppDependencies {
        val outFactory = SimpleConsoleOutFactory.standard()

        val commandRunner = TmuxCommandRunner()
        val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
        val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

        val glmDirectLLM = createGLMDirectLLM(outFactory)

        return AppDependencies(
            outFactory = outFactory,
            tmuxCommandRunner = commandRunner,
            tmuxCommunicator = communicator,
            tmuxSessionManager = sessionManager,
            glmDirectLLM = glmDirectLLM,
        )
    }

    private fun createGLMDirectLLM(outFactory: OutFactory): DirectLLM {
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
