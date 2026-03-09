# EXPLORATION REPORT: Add GLM5 API Caller

## Key Findings

### No directLLMApi package exists yet - needs to be created
### No HTTP client dependency in build.gradle.kts - must add one

## Project Structure
```
app/src/main/kotlin/
├── org/example/App.kt (main entry point)
└── com/glassthought/
    ├── Constants.kt (Config, ModelNamesConfig, LLM_MODEL_NAME)
    ├── initializer/Initializer.kt (currently empty)
    ├── processRunner/InteractiveProcessRunner.kt
    └── tmux/ (TmuxSessionManager, TmuxCommunicator, etc.)
```

## Constants.kt
```kotlin
package com.glassthought

object Constants {
  object LLM_MODEL_NAME {
    val GLM_HIGHEST_TIER = "GLM-5"
  }
  fun getConfigurationObject(): Config {
    return Config(zAiGlmConfig = ModelNamesConfig(highestTier = LLM_MODEL_NAME.GLM_HIGHEST_TIER))
  }
}

data class ModelNamesConfig(val highestTier: String)
data class Config(val zAiGlmConfig: ModelNamesConfig)
```

## Initializer.kt (empty)
```kotlin
package com.glassthought.initializer
class Initializer { }
```

## build.gradle.kts Dependencies
- `com.asgard:asgardCore:1.0.0` (logging, lifecycle)
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`
- `com.asgard:asgardTestTools:1.0.0` (test)
- kotest assertions + runner (test)
- **NO HTTP client dependency**

## Established Patterns
- **Constructor injection**: `class Foo(outFactory: OutFactory, private val dep: Dep)`
- **Out logging**: `outFactory.getOutForClass(Foo::class)`, snake_case messages, Val/ValType
- **Interfaces + Impl**: same file or companion, composition over inheritance
- **Suspend functions** for I/O
- **AsgardCloseable**: `.use{}` for cleanup
- **Tests**: `AsgardDescribeSpec`, BDD GIVEN/WHEN/THEN, one assert per `it` block
- **Integration tests**: `.config(isIntegTestEnabled())`, `@OptIn(ExperimentalKotest::class)`

## Files to Create
- `app/src/main/kotlin/com/glassthought/directLLMApi/DirectLLM.kt` - interface
- `app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt` - implementation

## Files to Modify
- `app/build.gradle.kts` - add HTTP client
- `app/src/main/kotlin/com/glassthought/initializer/Initializer.kt` - wire DirectLLM
