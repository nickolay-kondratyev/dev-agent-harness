---
id: nid_3ygjxcmvgff95uet7bc8x06zs_E
title: "refactor chainsaw context and spawn test"
status: in_progress
deps: []
links: []
created_iso: 2026-03-11T15:25:40Z
status_updated_iso: 2026-03-11T15:32:52Z
type: task
priority: 3
assignee: nickolaykondratyev
---


Right now in this test: $(git.repo_root)/app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt

We are spawning individual use cases and individual dependencies:
```kt file=[$(git.repo_root)/app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt] Lines=[47-52]
        val useCase = SpawnTmuxAgentSessionUseCase(
            agentTypeChooser = agentTypeChooser,
            bundleFactory = bundleFactory,
            tmuxSessionManager = sessionManager,
            outFactory = outFactory,
        )
```
```kt file=[$(git.repo_root)/app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt] Lines=[38-44]
        val bundleFactory: AgentStarterBundleFactory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = systemPromptFilePath,
            claudeProjectsDir = Path.of(System.getProperty("user.home"), ".claude", "projects"),
            outFactory = outFactory,
        )
```

There are 2 downsides to this:
1) there is more overhead to the tests.
2) the tests may not be testing what is in production.

Instead we now have:
```kt file=[$(git.repo_root)/app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedContextDescribeSpec.kt] Lines=[67-68]
    val chainsawContext: ChainsawContext = SharedContextIntegFactory.chainsawContext
```

This context will be very similar to what our production context will be like, and we should use it in the tests.

As part of this task we should do the following:
1) We should introduce grouping into ChainsawContext
    2) Let's group all the Tmux related dependencies together into TmuxInfra
    3) Let's group TmuxInfra and out factory under bigger group called Infra
        4) Under infra we will also have DirectLLMInfra
            5) Which will house the GLM direct LLM.
    4) Introduce UseCases group as well which will house the use cases.


```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt] Lines=[25-41]
class ChainsawContext(
    val outFactory: OutFactory,
    val tmuxCommandRunner: TmuxCommandRunner,
    val tmuxCommunicator: TmuxCommunicator,
    val tmuxSessionManager: TmuxSessionManager,
    val glmDirectLLM: DirectLLM,
    private val httpClient: OkHttpClient,
) : AsgardCloseable {

    override suspend fun close() {
        // Shut down OkHttpClient connection and thread pools to prevent resource leaks
        // in long-running server usage. Order matters: dispatcher first, then connections.
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        outFactory.close()
    }
}
```

Then in the test we will be able to just pull from the context instead of spawning individual dependencies and use cases.