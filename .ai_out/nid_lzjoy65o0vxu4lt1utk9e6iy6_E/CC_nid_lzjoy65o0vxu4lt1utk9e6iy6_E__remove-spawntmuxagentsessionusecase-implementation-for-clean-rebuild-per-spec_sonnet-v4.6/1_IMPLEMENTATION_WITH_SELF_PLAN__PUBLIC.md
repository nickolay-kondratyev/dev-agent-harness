# Implementation: Remove SpawnTmuxAgentSessionUseCase for Clean Rebuild

## Files Removed

### Main source files (10 total)
1. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/useCase/SpawnTmuxAgentSessionUseCase.kt`
   - The self-declared misaligned use case
2. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/data/StartAgentRequest.kt`
   - Input data class for `SpawnTmuxAgentSessionUseCase`, now orphaned
3. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/data/PhaseType.kt`
   - Enum used only by `StartAgentRequest`, now orphaned
4. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/AgentStarterBundleFactory.kt`
   - Interface used only by `SpawnTmuxAgentSessionUseCase`, now orphaned
5. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/AgentTypeChooser.kt`
   - Interface + `DefaultAgentTypeChooser` impl, used only by `SpawnTmuxAgentSessionUseCase`, now orphaned
6. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/impl/ClaudeCodeAgentStarterBundleFactory.kt`
   - Implementation of `AgentStarterBundleFactory`, now orphaned
7. `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/data/AgentStarterBundle.kt`
   - Data class pairing `AgentStarter` + `AgentSessionIdResolver`, used only by the removed factory, now orphaned
8. `app/src/test/kotlin/com/glassthought/bucket/SpawnTmuxAgentSessionUseCaseIntegTest.kt`
   - Integration test for removed use case
9. `app/src/test/kotlin/com/glassthought/ticketShepherd/core/agent/DefaultAgentTypeChooserTest.kt`
   - Unit test for removed `DefaultAgentTypeChooser`
10. `app/src/test/kotlin/com/glassthought/ticketShepherd/core/agent/impl/ClaudeCodeAgentStarterBundleFactoryTest.kt`
    - Unit test for removed `ClaudeCodeAgentStarterBundleFactory`

## Files Modified

### `app/src/main/kotlin/com/glassthought/ticketShepherd/core/initializer/Initializer.kt`
- Removed imports: `DefaultAgentTypeChooser`, `SpawnTmuxAgentSessionUseCase`, `ClaudeCodeAgentStarterBundleFactory`, `Environment`, `Path`
- Removed `UseCases` data class (was its only field)
- Removed `useCases: UseCases` parameter from `ShepherdContext` constructor
- Updated `ShepherdContext` KDoc to remove reference to `useCases`
- Removed `Initializer.initialize()` parameters: `environment`, `systemPromptFilePath`, `claudeProjectsDir` (were only used to configure the now-removed bundle factory)
- Removed all wiring code for `bundleFactory`, `agentTypeChooser`, `spawnTmuxAgentSession`, `useCases` in `InitializerImpl.initializeImpl()`
- Simplified `initializeImpl()` signature to `(outFactory, httpClient)`

### `app/src/test/kotlin/com/glassthought/ticketShepherd/integtest/SharedContextIntegFactory.kt`
- Removed imports: `Environment`, `java.io.File`
- Updated `Initializer.standard().initialize(...)` call to remove `environment` and `systemPromptFilePath` arguments
- Removed `resolveSystemPromptFilePath()` and `findGitRepoRoot()` helper methods

### `app/src/test/kotlin/com/glassthought/initializer/AppDependenciesCloseTest.kt`
- Removed `Environment` import
- Updated `Initializer.standard().initialize(...)` call to remove `environment = Environment.test()` argument

### `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/TmuxAgentSession.kt`
- Removed dangling KDoc reference to deleted `SpawnTmuxAgentSessionUseCase`

### `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/starter/impl/ClaudeCodeAgentStarter.kt`
- Removed dangling KDoc reference to deleted `ClaudeCodeAgentStarterBundleFactory`

### `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/HandshakeGuid.kt`
- Removed dangling comment reference to deleted `SpawnTmuxAgentSessionUseCase.md` doc

## Build Result

`bash _prepare_pre_build.sh && ./gradlew :app:build` — **SUCCESS**

## Test Result

`bash test.sh` — **SUCCESS**: 115 tests, 0 failures, 0 errors

## Notes

- The `Environment` class and `data/Environment.kt` were **not removed** — `Environment` is still used in `EnvironmentTest` (which tests it directly) and may be re-used when the new `SpawnTmuxAgentSessionUseCase` implementation is built per spec.
- The `TmuxAgentSession.kt` class was retained — it is a legitimate domain concept (pairs a tmux session handle with a resolved session ID) that will be used by the new implementation.
- `ClaudeCodeAgentStarter.kt` was retained — it builds the claude CLI command and is independently valid.
- No new functionality was added; this was a pure removal.
