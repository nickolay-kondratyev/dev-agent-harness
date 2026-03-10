# Exploration: Add Environment Interface

## Key Findings

### Initializer.kt Current State
- `interface Initializer` with `suspend fun initialize(): AppDependencies`
- `InitializerImpl` implements it; `companion object` provides `standard()` factory
- `AppMain.kt` calls `InitializerImpl().initialize()` inside `runBlocking {}`

### Target Location
- New file: `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt`
- No `data/` subdirectory exists yet under `initializer/`

### Required Changes
1. **Create** `Environment` interface with `val isTest: Boolean`
   - `ProductionEnvironment` impl: `isTest = false`
   - `TestEnvironment` impl: `isTest = true`
   - `companion object` with `fun production(): Environment` factory
2. **Update** `Initializer.initialize()` signature:
   `suspend fun initialize(environment: Environment = Environment.production()): AppDependencies`
3. **Update** `InitializerImpl.initialize()` to accept and pass through the parameter
4. **Update** `AppMain.kt` call (no change needed - uses default)

### Test Patterns
- Extend `AsgardDescribeSpec`, BDD `describe/it` style
- One assertion per `it` block
- No existing `InitializerTest.kt`

### No Ambiguities
Requirements are clear. No clarification needed.
