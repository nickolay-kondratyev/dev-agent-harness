# Private Context: ClaudeCodeAdapter Spawn Flow Integration Test

## Status: COMPLETE

## What Was Implemented

### GlmConfig (ap.8BYTb6vcyAzpWavQguBrb.E)
- Location: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/GlmConfig.kt`
- Data class with 5 fields: baseUrl, authToken, defaultOpusModel, defaultSonnetModel, defaultHaikuModel
- `toEnvVarExports()` produces shell export string
- `standard(authToken)` factory with Z.AI defaults

### ClaudeCodeAdapter Changes (ap.gCgRdmWd9eTGXPbHJvyxI.E)
- Added `glmConfig: GlmConfig?` constructor param (default null)
- `buildStartCommand()`: when glmConfig non-null, prepends `${glmConfig.toEnvVarExports()} && ` before `cd`
- `create()` factory updated to pass through glmConfig

### ContextInitializer Changes (ap.9zump9YISPSIcdnxEXZZX.E)
- Extracted `readZaiApiKey()` private method
- ZAI API key read once, shared between NonInteractiveAgentRunner and ClaudeCodeAdapter
- `GlmConfig.standard(authToken = zaiApiKey)` wired into `ClaudeCodeAdapter.create()`

### Unit Tests
- 14 new test cases in ClaudeCodeAdapterTest covering GLM injection and GlmConfig.standard()

### Integration Test
- Location: `app/src/test/kotlin/com/glassthought/shepherd/integtest/ClaudeCodeAdapterSpawnIntegTest.kt`
- Tests: session creation, session ID resolution, command content validation
- Requires: tmux, GLM token, `-PrunIntegTests=true`

## Design Decisions

1. GlmConfig is always wired in ContextInitializer (production always redirects to GLM)
   - If this becomes an issue, add a boolean flag to control it
2. GLM env vars placed before `cd` in innerCommand, matching deep memory spec
3. Integration test uses `shepherdContext.infra.claudeCode.agentTypeAdapter` cast to `ClaudeCodeAdapter`
   for access to `buildStartCommand()` and `resolveSessionId()` — this is safe because the
   SharedContextIntegFactory always wires a ClaudeCodeAdapter

## Potential Follow-ups
- If production needs to NOT redirect to GLM, add a config flag (currently always redirected)
- The `Environment.isTest` concept mentioned in deep memory was NOT implemented — instead,
  GlmConfig is nullable and controlled by the wiring layer
