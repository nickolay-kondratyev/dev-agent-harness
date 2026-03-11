---
closed_iso: 2026-03-11T15:48:29Z
id: nid_3ygjxcmvgff95uet7bc8x06zs_E
title: "refactor shepherd context and spawn test"
status: closed
deps: []
links: []
created_iso: 2026-03-11T15:25:40Z
status_updated_iso: 2026-03-11T15:48:29Z
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
```kt file=[$(git.repo_root)/app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt] Lines=[67-68]
    val shepherdContext: ShepherdContext = SharedContextIntegFactory.shepherdContext
```

This context will be very similar to what our production context will be like, and we should use it in the tests.

As part of this task we should do the following:
1) We should introduce grouping into ShepherdContext
    2) Let's group all the Tmux related dependencies together into TmuxInfra
    3) Let's group TmuxInfra and out factory under bigger group called Infra
        4) Under infra we will also have DirectLLMInfra
            5) Which will house the GLM direct LLM.
    4) Introduce UseCases group as well which will house the use cases.


```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/Initializer.kt] Lines=[25-41]
class ShepherdContext(
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

## Resolution

### Completed

1. **Created grouping data classes** in `Initializer.kt`:
   - `TmuxInfra` — groups `commandRunner`, `communicator`, `sessionManager`
   - `DirectLlmInfra` — groups `glmDirectLLM`, `httpClient`
   - `Infra` — groups `outFactory`, `tmux: TmuxInfra`, `directLlm: DirectLlmInfra`
   - `UseCases` — groups `spawnTmuxAgentSession: SpawnTmuxAgentSessionUseCase`

2. **Refactored ShepherdContext** from flat fields to `infra: Infra` + `useCases: UseCases`

3. **Wired SpawnTmuxAgentSessionUseCase into Initializer** — added `systemPromptFilePath` and `claudeProjectsDir` params

4. **Simplified SpawnTmuxAgentSessionUseCaseIntegTest** — now uses `shepherdContext.useCases.spawnTmuxAgentSession` instead of manually constructing all dependencies

5. **Updated all references** across 9 files (tests + production code)

6. **Fixed DRY violation** in `AppDependenciesCloseTest` — added `httpClient` param to `Initializer.initialize()` so the test can inject a custom httpClient without duplicating all wiring

7. **Updated documentation** — `SharedContextDescribeSpec` KDoc, `4_testing_standards.md`, and regenerated `CLAUDE.md`