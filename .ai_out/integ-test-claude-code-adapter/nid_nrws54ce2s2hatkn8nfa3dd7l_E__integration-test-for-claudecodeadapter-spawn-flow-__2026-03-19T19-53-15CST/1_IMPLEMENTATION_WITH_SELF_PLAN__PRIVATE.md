# Private Context: ClaudeCodeAdapter Spawn Flow Integration Test

## Status: ITERATION COMPLETE (addressing review feedback)

## What Was Changed in Iteration

### GlmConfig Shell Injection Fix
- `toEnvVarExports()` now wraps dynamic values in double quotes
- Unit tests updated to expect quoted format

### GlmConfig Made Conditional
- `ContextInitializerImpl` gains `glmEnabled: Boolean = false` constructor param
- `ContextInitializer.forIntegTest()` factory added for test wiring
- `SharedContextIntegFactory` switched from `standard()` to `forIntegTest()`
- Production `standard()` does NOT enable GLM

### Integration Test Restructured
- Removed command-string inspection block (covered by unit tests)
- Added send-keys verification test case
- Replaced unsafe `as ClaudeCodeAdapter` with `require(adapter is ClaudeCodeAdapter)`
- Three test cases now: session exists, session ID resolution, send-keys acceptance

## Files Modified in Iteration

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/GlmConfig.kt` | Quoted env var values |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` | Added `glmEnabled` param, `forIntegTest()` factory |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextIntegFactory.kt` | Use `forIntegTest()` |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/ClaudeCodeAdapterSpawnIntegTest.kt` | Restructured: removed command inspection, added send-keys, fixed cast |
| `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapterTest.kt` | Updated assertions for quoted env var values |

## Design Decisions

1. `glmEnabled` is a simple boolean — no need for a more complex factory/strategy pattern
2. `require()` used instead of safe cast `as?` because the test should fail fast with a clear message
3. Send-keys test verifies session accepts input AND remains alive afterward
4. Bootstrap handshake (`/signal/started`) cannot be directly tested without harness server — session ID resolution serves as indirect verification
