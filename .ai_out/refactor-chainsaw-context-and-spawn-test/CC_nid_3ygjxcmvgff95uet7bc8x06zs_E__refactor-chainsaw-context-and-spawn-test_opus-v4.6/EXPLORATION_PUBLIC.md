# Exploration Findings

## ChainsawContext (Initializer.kt:24-41)
Current flat structure with 5 public fields + 1 private:
- `outFactory: OutFactory`
- `tmuxCommandRunner: TmuxCommandRunner`
- `tmuxCommunicator: TmuxCommunicator`
- `tmuxSessionManager: TmuxSessionManager`
- `glmDirectLLM: DirectLLM`
- `httpClient: OkHttpClient` (private, for close())

## SpawnTmuxAgentSessionUseCaseIntegTest
- Extends `SharedContextDescribeSpec` but manually constructs `ClaudeCodeAgentStarterBundleFactory`, `DefaultAgentTypeChooser`, and `SpawnTmuxAgentSessionUseCase`
- Only pulls `tmuxSessionManager` from shared context

## Only UseCase: SpawnTmuxAgentSessionUseCase
Dependencies: `agentTypeChooser`, `bundleFactory`, `tmuxSessionManager`, `outFactory`

## Shared Test Infra
- `SharedContextIntegFactory` - process-scoped singleton creating `ChainsawContext` via `Initializer.standard()`
- `SharedContextDescribeSpec` - base class exposing `chainsawContext` property

## Tests using SharedContextDescribeSpec
- SpawnTmuxAgentSessionUseCaseIntegTest
- TmuxCommunicatorIntegTest
- TmuxSessionManagerIntegTest

## Tests with manual construction (NOT using shared context)
- AppDependenciesCloseTest - needs custom httpClient for close testing
- GLMHighestTierApiIntegTest - tests GLMHighestTierApi in isolation
- GitBranchManagerIntegTest - doesn't need ChainsawContext
