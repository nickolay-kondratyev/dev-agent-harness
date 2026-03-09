package com.glassthought.initializer

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.initializer.AppDependencies
import com.glassthought.chainsaw.core.directLLMApi.glm.GLMHighestTierApi
import com.glassthought.chainsaw.core.tmux.TmuxCommunicatorImpl
import com.glassthought.chainsaw.core.tmux.TmuxSessionManager
import com.glassthought.chainsaw.core.tmux.util.TmuxCommandRunner
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient

/**
 * Verifies that [AppDependencies] properly implements [com.asgard.core.lifecycle.AsgardCloseable]
 * and shuts down [OkHttpClient] resources on close.
 */
class AppDependenciesCloseTest : AsgardDescribeSpec({

    fun buildDepsWithHttpClient(httpClient: OkHttpClient): AppDependencies {
        val commandRunner = TmuxCommandRunner()
        val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
        return AppDependencies(
          outFactory = outFactory,
          tmuxCommandRunner = commandRunner,
          tmuxCommunicator = communicator,
          tmuxSessionManager = TmuxSessionManager(outFactory, commandRunner, communicator),
          glmDirectLLM = GLMHighestTierApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = "test-model",
            maxTokens = 100,
            apiEndpoint = "http://localhost/test",
            apiToken = "test-token",
          ),
          httpClient = httpClient,
        )
    }

    describe("GIVEN AppDependencies") {
        describe("WHEN close() is called") {
            it("THEN OkHttpClient dispatcher executor service is shut down") {
                val httpClient = OkHttpClient.Builder().build()
                val deps = buildDepsWithHttpClient(httpClient)

                deps.close()

                httpClient.dispatcher.executorService.isShutdown shouldBe true
            }
        }
    }
})
