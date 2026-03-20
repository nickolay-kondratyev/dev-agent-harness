# Exploration: AppMain Implementation

## Current State
- `AppMain.kt` exists with `TODO("CLI not yet implemented")` after `EnvironmentValidator.validate()`
- No picocli dependency in build

## CLI Spec (from high-level.md ap.mmcagXtg6ulznKYYNKlNP.E)
```
shepherd run --workflow <name> --ticket <path> --iteration-max <N>
```
- `--ticket` **(required)**: Path to ticket markdown
- `--workflow`: Workflow definition name (e.g. `straightforward`)
- `--iteration-max` **(required)**: Default iteration budget

## Startup Sequence (ap.HRlQHC1bgrTRyRknP3WNX.E)
0. `EnvironmentValidator.validate()` — already done
1. `ContextInitializer.standard().initialize(outFactory)` → `ShepherdContext`
2. `ShepherdServer` startup — Ktor CIO on `TICKET_SHEPHERD_SERVER_PORT` env var
3. `TicketShepherdCreator.create(shepherdContext, ticketPath, workflowName)` → `TicketShepherd`
4. `TicketShepherd.run()` — drives workflow
5. Cleanup — `ShepherdContext.close()`

## Key Interfaces
- `ContextInitializer`: `fun interface` with `suspend fun initialize(outFactory: OutFactory): ShepherdContext`
  - Factory: `ContextInitializer.standard()`
- `ShepherdServer(sessionsState: SessionsState, outFactory: OutFactory)` — `configureApplication(Application)`
- `TicketShepherdCreator`: `fun interface` with `suspend fun create(shepherdContext, ticketPath, workflowName): TicketShepherd`
- `SessionsState()` — simple constructor, no args
- Server port: `Constants.AGENT_COMM.SERVER_PORT_ENV_VAR` = `"TICKET_SHEPHERD_SERVER_PORT"`

## Server start pattern (from integ tests)
```kotlin
val ktorServer = embeddedServer(CIO, port = serverPort) {
    shepherdServer.configureApplication(this)
}.start(wait = false)
```

## OutFactory creation
- `ContextInitializer.initialize()` takes an `OutFactory` as parameter — caller must create it
- In tests: `NoOpOutFactory()`
- Production: need to check AsgardCore for production factory

## Dependencies needed
- picocli for CLI parsing (not yet in build)
- `TicketShepherdCreatorImpl` has many constructor deps with TODOs for production wiring

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/server/ShepherdServer.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionsState.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
