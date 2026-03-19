# Implementation: Wire NonInteractiveAgentRunner into ShepherdContext

## Summary

Wired `NonInteractiveAgentRunner` into `ShepherdContext` via `ContextInitializerImpl`, with proper fail-hard semantics for the ZAI API key file and injectable seams for testability.

## Changes Made

### 1. Constants.kt — Added AI_MODEL_ZAI_FAST
- Added `const val AI_MODEL_ZAI_FAST = "AI_MODEL__ZAI__FAST"` to `REQUIRED_ENV_VARS`
- Added to `REQUIRED_ENV_VARS.ALL` list so `EnvironmentValidator` validates it at startup
- Added KDoc explaining purpose (used by NonInteractiveAgentRunner for PI CLI)

### 2. ShepherdContext.kt — Added nonInteractiveAgentRunner property
- Added `val nonInteractiveAgentRunner: NonInteractiveAgentRunner` as a top-level constructor parameter (NOT inside Infra)

### 3. ContextInitializer.kt — Wired NonInteractiveAgentRunnerImpl
- Added three injectable seams to `ContextInitializerImpl` constructor (following EnvironmentValidator pattern):
  - `envVarReader: (String) -> String?` — for reading MY_ENV
  - `fileReader: (Path) -> String` — for reading ZAI API key file
  - `processRunnerFactory: (OutFactory) -> ProcessRunner` — for creating ProcessRunner
- All have production defaults (`System::getenv`, `Path.toFile().readText()`, `ProcessRunner.standard()`)
- `createNonInteractiveAgentRunner()` reads ZAI API key from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN`
- Fails hard with `IllegalStateException` if:
  - MY_ENV env var is not set
  - ZAI API key file cannot be read (IOException)
  - ZAI API key file is empty or whitespace-only

### 4. ContextInitializerTest.kt — New test class
- BDD style with GIVEN/WHEN/THEN, one assert per `it` block
- Tests:
  - Valid config returns ShepherdContext with nonInteractiveAgentRunner wired
  - Missing MY_ENV env var fails hard
  - Missing ZAI API key file fails hard with "ZAI API key" message
  - Empty ZAI API key file fails hard with "empty" message
  - Whitespace-only ZAI API key file fails hard with "empty" message

### 5. EnvironmentValidatorTest.kt — Updated for new env var
- Added `AI_MODEL_ZAI_FAST` to `allEnvVarsPresent` map so existing tests pass with the expanded `REQUIRED_ENV_VARS.ALL`

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializerTest.kt` (new)
- `app/src/test/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidatorTest.kt`

## Test Results
All tests pass: `./gradlew :app:test` — BUILD SUCCESSFUL
