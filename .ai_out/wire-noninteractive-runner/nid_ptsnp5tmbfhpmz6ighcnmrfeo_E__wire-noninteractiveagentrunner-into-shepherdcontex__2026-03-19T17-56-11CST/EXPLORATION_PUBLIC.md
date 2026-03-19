# Exploration: Wire NonInteractiveAgentRunner into ShepherdContext

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt` | Context holder — add `nonInteractiveAgentRunner` as top-level property |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` | Wiring — create NonInteractiveAgentRunnerImpl in `initializeImpl()` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt` | Add `AI_MODEL_ZAI_FAST` constant to REQUIRED_ENV_VARS |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt` | No changes — auto-validates from Constants.REQUIRED_ENV_VARS.ALL |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunner.kt` | Interface + data classes (already implemented) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImpl.kt` | Implementation (already implemented) |

## Key Discovery: ProcessRunner Instantiation

`ProcessRunner.standard(outFactory)` — found in `GitBranchManagerIntegTest.kt`. This is how ProcessRunner is created from asgardCore.

## NonInteractiveAgentRunnerImpl Constructor

```kotlin
class NonInteractiveAgentRunnerImpl(
    private val processRunner: ProcessRunner,
    outFactory: OutFactory,
    private val zaiApiKey: String,
)
```

Needs: ProcessRunner, OutFactory (already available), zaiApiKey (read from file).

## Current ShepherdContext

```kotlin
class ShepherdContext(
    val infra: Infra,
    val timeoutConfig: HarnessTimeoutConfig = HarnessTimeoutConfig.defaults(),
) : AsgardCloseable by infra
```

## Current ContextInitializerImpl.initializeImpl()

Creates TmuxInfra, ClaudeCodeInfra, Infra, returns ShepherdContext(infra=infra).

## ZAI API Key

Ticket says: Read from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN` — fail hard with IllegalStateException if missing/empty.

## AI_MODEL__ZAI__FAST Env Var

Ticket says: Add `AI_MODEL_ZAI_FAST` named constant, add to `REQUIRED_ENV_VARS.ALL`.

## No Existing ContextInitializer Tests

No test files found for ContextInitializer — need to create new test.

## EnvironmentValidator Pattern (for test reference)

Uses injectable `envVarReader: (String) -> String?` and `dockerEnvFilePath: Path` for testability.
ContextInitializerImpl will need similar injectable seams for file reading and env var access.
