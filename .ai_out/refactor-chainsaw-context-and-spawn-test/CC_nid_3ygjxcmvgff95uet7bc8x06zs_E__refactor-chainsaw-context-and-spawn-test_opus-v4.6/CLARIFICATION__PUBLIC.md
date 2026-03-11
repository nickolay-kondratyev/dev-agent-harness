# Clarification

## Requirements (from ticket)
1. Group ChainsawContext fields into logical sub-groups:
   - `TmuxInfra`: TmuxCommandRunner, TmuxCommunicator, TmuxSessionManager
   - `DirectLlmInfra`: glmDirectLLM (+ httpClient, since it's LLM-specific)
   - `Infra`: TmuxInfra + DirectLlmInfra + OutFactory
   - `UseCases`: SpawnTmuxAgentSessionUseCase (and future use cases)
2. Update Initializer to build these groups
3. Simplify SpawnTmuxAgentSessionUseCaseIntegTest to pull use case from shared context

## Key Decision: Wiring SpawnTmuxAgentSessionUseCase
To have `SpawnTmuxAgentSessionUseCase` in context, the Initializer must also create:
- `ClaudeCodeAgentStarterBundleFactory` (needs: environment, systemPromptFilePath, claudeProjectsDir, outFactory)
- `DefaultAgentTypeChooser` (no dependencies)

Approach: Add `systemPromptFilePath: String? = null` and `claudeProjectsDir: Path` (with default `~/.claude/projects`) as parameters to `Initializer.initialize()`.

## No Blocking Ambiguities
Requirements are specific enough to proceed.
