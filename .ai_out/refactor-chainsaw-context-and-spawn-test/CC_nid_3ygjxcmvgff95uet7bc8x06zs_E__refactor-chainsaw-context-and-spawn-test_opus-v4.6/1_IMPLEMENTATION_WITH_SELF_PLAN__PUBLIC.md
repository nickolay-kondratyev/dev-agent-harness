# Refactor ChainsawContext: Logical Grouping of Dependencies

## Summary

Refactored `ChainsawContext` from a flat structure with 5+ public fields to a logically grouped structure using data classes. Also wired `SpawnTmuxAgentSessionUseCase` into the context and simplified `SpawnTmuxAgentSessionUseCaseIntegTest` to leverage the shared context.

## What Was Done

### 1. Created Grouping Data Classes (in Initializer.kt)
- **`TmuxInfra`** — groups `commandRunner`, `communicator`, `sessionManager`
- **`DirectLlmInfra`** — groups `glmDirectLLM`, `httpClient` (internal visibility for httpClient since it's only needed for close())
- **`Infra`** — top-level grouping: `outFactory`, `tmux: TmuxInfra`, `directLlm: DirectLlmInfra`
- **`UseCases`** — groups `spawnTmuxAgentSession: SpawnTmuxAgentSessionUseCase`

### 2. Refactored ChainsawContext
- Replaced flat fields with `infra: Infra` + `useCases: UseCases`
- `close()` now accesses `infra.directLlm.httpClient` for cleanup
- Preserved anchor point `ap.TkpljsXvwC6JaAVnIq02He98.E`

### 3. Updated Initializer
- Added `systemPromptFilePath: String? = null` and `claudeProjectsDir: Path` parameters to `initialize()`
- Wires `ClaudeCodeAgentStarterBundleFactory`, `DefaultAgentTypeChooser`, and `SpawnTmuxAgentSessionUseCase`

### 4. Simplified SpawnTmuxAgentSessionUseCaseIntegTest
- Removed manual construction of `ClaudeCodeAgentStarterBundleFactory`, `DefaultAgentTypeChooser`, `SpawnTmuxAgentSessionUseCase`
- Removed `resolveSystemPromptFilePath()` and `findGitRepoRoot()` helper functions
- Now uses `chainsawContext.useCases.spawnTmuxAgentSession` and `chainsawContext.infra.tmux.sessionManager`

### 5. Updated SharedContextIntegFactory
- Passes `systemPromptFilePath` resolved from repo root when initializing test context
- Added `resolveSystemPromptFilePath()` and `findGitRepoRoot()` here (moved from test)

### 6. Updated All Other References
- `TmuxCommunicatorIntegTest`: `chainsawContext.tmuxSessionManager` -> `chainsawContext.infra.tmux.sessionManager`
- `TmuxSessionManagerIntegTest`: same change
- `AppDependenciesCloseTest`: updated manual ChainsawContext construction to use new grouped structure
- `AppMain.kt`: `deps.tmuxSessionManager` -> `deps.infra.tmux.sessionManager`
- `CallGLMApiSandboxMain.kt`: `.glmDirectLLM` -> `.infra.directLlm.glmDirectLLM`
- `SharedContextDescribeSpec.kt`: updated KDoc examples

## Files Modified

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt` | Grouping data classes + refactored ChainsawContext + updated Initializer |
| `app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt` | Simplified to use shared context |
| `app/src/test/kotlin/org/example/TmuxCommunicatorIntegTest.kt` | Updated field access path |
| `app/src/test/kotlin/org/example/TmuxSessionManagerIntegTest.kt` | Updated field access path |
| `app/src/test/kotlin/com/glassthought/initializer/AppDependenciesCloseTest.kt` | Updated manual construction |
| `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedContextIntegFactory.kt` | Added systemPromptFilePath + repo root resolution |
| `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedContextDescribeSpec.kt` | Updated KDoc |
| `app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt` | Updated field access path |
| `app/src/main/kotlin/com/glassthought/chainsaw/cli/sandbox/CallGLMApiSandboxMain.kt` | Updated field access path |

## Tests

- Unit tests: BUILD SUCCESSFUL (all pass)
- Integration tests: NOT run (require tmux + claude CLI as expected)

## Decisions

1. **Grouping classes in same file as ChainsawContext**: Placed `TmuxInfra`, `DirectLlmInfra`, `Infra`, and `UseCases` in `Initializer.kt` since they are part of the context structure and closely related. This keeps the types co-located with their primary consumer.

2. **`internal` visibility for `DirectLlmInfra.httpClient`**: Used `internal` instead of `private` because `data class` properties cannot be `private`. `internal` constrains access to the module while allowing the data class copy/equals/toString to work. The KDoc clearly documents why this field exists (resource cleanup only).

3. **`resolveSystemPromptFilePath` moved to SharedContextIntegFactory**: The logic was extracted from the test into the factory where it belongs — the factory is responsible for configuring the shared context including the system prompt path.
