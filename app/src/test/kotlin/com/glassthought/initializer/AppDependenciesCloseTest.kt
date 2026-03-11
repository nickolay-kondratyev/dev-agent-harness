package com.glassthought.initializer

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.agent.DefaultAgentTypeChooser
import com.glassthought.chainsaw.core.agent.SpawnTmuxAgentSessionUseCase
import com.glassthought.chainsaw.core.agent.impl.ClaudeCodeAgentStarterBundleFactory
import com.glassthought.chainsaw.core.directLLMApi.glm.GLMHighestTierApi
import com.glassthought.chainsaw.core.initializer.ChainsawContext
import com.glassthought.chainsaw.core.initializer.DirectLlmInfra
import com.glassthought.chainsaw.core.initializer.Infra
import com.glassthought.chainsaw.core.initializer.TmuxInfra
import com.glassthought.chainsaw.core.initializer.UseCases
import com.glassthought.chainsaw.core.initializer.data.Environment
import com.glassthought.chainsaw.core.tmux.TmuxCommunicatorImpl
import com.glassthought.chainsaw.core.tmux.TmuxSessionManager
import com.glassthought.chainsaw.core.tmux.util.TmuxCommandRunner
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import java.nio.file.Path

/**
 * Verifies that [ChainsawContext] properly implements [com.asgard.core.lifecycle.AsgardCloseable]
 * and shuts down [OkHttpClient] resources on close.
 */
class AppDependenciesCloseTest : AsgardDescribeSpec({

    fun buildDepsWithHttpClient(httpClient: OkHttpClient): ChainsawContext {
        val commandRunner = TmuxCommandRunner()
        val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
        val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

        val tmuxInfra = TmuxInfra(
            commandRunner = commandRunner,
            communicator = communicator,
            sessionManager = sessionManager,
        )

        val directLlmInfra = DirectLlmInfra(
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

        val infra = Infra(
            outFactory = outFactory,
            tmux = tmuxInfra,
            directLlm = directLlmInfra,
        )

        val bundleFactory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = null,
            claudeProjectsDir = Path.of(System.getProperty("user.home"), ".claude", "projects"),
            outFactory = outFactory,
        )

        val useCases = UseCases(
            spawnTmuxAgentSession = SpawnTmuxAgentSessionUseCase(
                agentTypeChooser = DefaultAgentTypeChooser(),
                bundleFactory = bundleFactory,
                tmuxSessionManager = sessionManager,
                outFactory = outFactory,
            ),
        )

        return ChainsawContext(
            infra = infra,
            useCases = useCases,
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
