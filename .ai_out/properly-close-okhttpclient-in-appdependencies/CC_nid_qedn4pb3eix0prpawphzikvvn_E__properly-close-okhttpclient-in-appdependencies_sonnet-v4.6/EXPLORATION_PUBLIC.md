# Exploration: Properly Close OkHttpClient in AppDependencies

## Key Files
- `app/src/main/kotlin/com/glassthought/initializer/Initializer.kt` — Contains `AppDependencies` data class + `InitializerImpl`
- `app/src/main/kotlin/org/example/App.kt` — Entry point; currently uses `deps.outFactory.use {}`
- `submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/.../lifecycle/AsgardCloseable.kt` — `suspend fun close()` interface
- `submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/.../lifecycle/AsgardCloseableExtensions.kt` — `use {}` extension

## Current State
- `AppDependencies` is a `data class` with no lifecycle/close behavior
- `OkHttpClient` is created inside `createGLMDirectLLM()` — NOT stored in `AppDependencies`, never closed
- `App.kt` uses `deps.outFactory.use {}` — only closes `outFactory`, not `httpClient`

## Solution
1. Change `AppDependencies` to a regular `class` implementing `AsgardCloseable`
2. Create `httpClient` in `initialize()`, pass to both `createGLMDirectLLM()` and `AppDependencies`
3. `AppDependencies.close()` shuts down `httpClient.dispatcher.executorService` + `httpClient.connectionPool.evictAll()` + `outFactory.close()`
4. `App.kt` switches to `deps.use {}` instead of `deps.outFactory.use {}`
