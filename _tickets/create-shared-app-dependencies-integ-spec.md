---
closed_iso: 2026-03-11T14:52:07Z
id: nid_q1s3us9fsufo5uglosuthzkr4_E
title: "Create shared app dependencies integ spec"
status: closed
deps: []
links: []
created_iso: 2026-03-11T13:19:44Z
status_updated_iso: 2026-03-11T14:52:07Z
type: task
priority: 3
assignee: nickolaykondratyev
---

Right now we are using AsgardDescribeSpec directly:

```kt file=[$(git.repo_root)/app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt] Lines=[33-34]
class SpawnTmuxAgentSessionUseCaseIntegTest : AsgardDescribeSpec({
```

With that we are also initializing things inline:
```kt file=[$(git.repo_root)/app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt] Lines=[43-48]
        val bundleFactory: AgentStarterBundleFactory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = systemPromptFilePath,
            claudeProjectsDir = Path.of(System.getProperty("user.home"), ".claude", "projects"),
            outFactory = outFactory,
        )
```

What I would like is to separate the common initialization that we are going to be doing from the actual test definitions.

So we will want to have `SharedAppDepIntegFactory` which will be in charge of creating a single Initializer with integ environment and will be used across all typical integration tests.

With that said though this `SharedAppDepIntegFactory` will need to provide AsgardDescribeSpecConfig


```kt file=[${MAVEN_LOCAL_REPOSITORY_DIR}/com/asgard/asgardTestTools-jvm/1.0.0/asgardTestTools-jvm-1.0.0-sources.jar!/commonMain/com/asgard/testTools/describe_spec/AsgardDescribeSpecConfig.kt] Lines=[48-57]
data class AsgardDescribeSpecConfig(
  val shouldDumpOutLinesOnTestError: Boolean = true,
  val afterTestLogLevelVerifyConfig: AfterTestLogLevelVerifyConfig = AfterTestLogLevelVerifyConfig.DEFAULT,
  @AnchorPoint("anchor_point.8ykbhmsrff3cersiqqtbjno")
  val autoClearOutLinesAfterTest: Boolean = false,
  val overrideLogLevelProvider: LogLevelProvider? = null,
  val shouldStopOnFirstFailure: Boolean = false,
  val testOutManager: TestOutManager = TestOutManagerStatic.getInstance(),
  val enableStructuredTestReporter: Boolean = true,
) {
```

And so later agents dont forget to provide it let's create `SharedAppDepDescribeSpec` and udpate our ai_input instructions to use `SharedAppDepDescribeSpec` as the default describe spec for integration tests. `SharedAppDepDescribeSpec` will extend `AsgardDescribeSpec` but it will have its own configuration `SharedAppDepSpecConfig` by default this configuration will take values from the SharedAppDepIntegFactory (SharedAppDepIntegFactory will be static class with METHODs that are called to get instances of objects including the important piece of getTestOutManager(), which will be in charge of providing the OutFactory)

We will add outFactory to be a required field of initialize instead of default console factory

```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt] Lines=[59-60]
    override suspend fun initialize(environment: Environment): AppDependencies {
        val outFactory = SimpleConsoleOutFactory.standard()
```

and use the value from the SharedAppDepIntegFactory (which will pull from TestOutManager)
```kt file=[${MAVEN_LOCAL_REPOSITORY_DIR}/com/asgard/asgardCore-jvm/1.0.0/asgardCore-jvm-1.0.0-sources.jar!/commonMain/com/asgard/core/out/impl/for_tests/testout/TestOutManager.kt] Lines=[15-20]
interface TestOutManager {
  companion object {
    fun standard(): TestOutManager = TestOutManagerStaticWrapImpl.standard()
  }

  val outFactory: OutFactory
```

`SharedAppDepDescribeSpec` by default will NOT need to have configuration provided as `SharedAppDepSpecConfig` will have reasonable defaults pulling from `SharedAppDepIntegFactory`.

`SharedAppDepIntegFactory` - will have a single instance of initialize and a single instance of AppDependencies

```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt] Lines=[24-41]
class AppDependencies(
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

Which will be shared across tests.

We will encapsulate the information on usage of the `SharedAppDepDescribeSpec` in `SharedAppDepDescribeSpec` KDoc and point to it by name and anchor point from auto loaded memory.
## Notes

**2026-03-11T14:52:03Z**

Resolution: Implementation complete on branch CC_nid_q1s3us9fsufo5uglosuthzkr4_E__create-shared-app-dependencies-integ-spec_sonnet-v4.6

## Created Files
- app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepIntegFactory.kt
- app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt (anchor: ap.20lFzpGIVAbuIXO5tUTBg.E)

## Modified Files
- app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt (outFactory as required parameter)
- app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt
- app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt (migrated to SharedAppDepDescribeSpec)
- app/src/test/kotlin/org/example/TmuxCommunicatorIntegTest.kt (migrated)
- app/src/test/kotlin/org/example/TmuxSessionManagerIntegTest.kt (migrated)
- ai_input/memory/auto_load/4_testing_standards.md (added guidance section)

## Follow-up Tickets
- nid_nwg1em2siekphpqeeuhrtl5wk_E: Fix logging violations in SpawnTmuxAgentSessionUseCaseIntegTest
- nid_g3z2de5zpq5dz608l9c651tam_E: Fix resource leak in CallGLMApiSandboxMain
