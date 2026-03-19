# Implementation: AppMain CLI Entry Point

## What Was Done

Implemented the full CLI entry point for the shepherd harness using picocli for argument parsing and a new `ShepherdInitializer` class for orchestrating the startup sequence.

### Changes

1. **Added picocli dependency** (v4.7.6) to `gradle/libs.versions.toml` and `app/build.gradle.kts`.

2. **Created `ShepherdInitializer`** (`app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializer.kt`):
   - Orchestrates the full startup sequence (Steps 1-4 per spec ref.ap.HRlQHC1bgrTRyRknP3WNX.E)
   - Step 1: `ContextInitializer.initialize(outFactory)` -> `ShepherdContext`
   - Step 2: Start embedded Ktor CIO HTTP server on `TICKET_SHEPHERD_SERVER_PORT` env var
   - Step 3: `TicketShepherdCreator.create(shepherdContext, ticketPath, workflowName)` -> `TicketShepherd`
   - Step 4: `TicketShepherd.run()` — drives workflow (never returns)
   - Cleanup via try/finally ensures resources close in reverse order
   - Constructor-injected dependencies for testability: `ServerStarter`, `serverPortReader`, `TicketShepherdCreatorFactory`
   - New anchor point: `ap.mFo35x06vJbjMQ8m7Lh4Z.E`

3. **Updated `AppMain.kt`** (`app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt`):
   - Added picocli `@Command` classes: `ShepherdRunCommand` (top-level) and `RunSubcommand` (`run` subcommand)
   - CLI: `shepherd run --workflow <name> --ticket <path> --iteration-max <N>`
   - All three args are required
   - Production wiring in `RunSubcommand.call()`: `SimpleConsoleOutFactory.standard()` for logging, production implementations for all `TicketShepherdCreatorImpl` dependencies
   - Preserved anchor point `ap.4JVSSyLwZXop6hWiJNYevFQX.E`

4. **Added unit tests** (`app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt`):
   - Tests for all steps: ContextInitializer is called, server starts on correct port, TicketShepherdCreator receives correct params
   - Cleanup tests: server stopped on exit, ShepherdContext closed on server failure, no server started when ContextInitializer fails
   - BDD style with GIVEN/WHEN/THEN

### Supporting Types Created in `ShepherdInitializer.kt`

| Type | Purpose |
|------|---------|
| `CliParams` | Clean data class for CLI args (no picocli annotations leak into core) |
| `ServerStarter` | Fun interface for starting Ktor server (testability seam) |
| `StoppableServer` | Fun interface for stopping server during cleanup |
| `KtorServerStarter` | Production implementation using `embeddedServer(CIO, ...)` |
| `TicketShepherdCreatorFactory` | Fun interface to create `TicketShepherdCreator` with production wiring |

### Tests

All tests pass: `./gradlew :app:test` (includes detekt static analysis).

## Decisions

- **Picocli subcommand structure**: Used `shepherd` as top-level command with `run` as subcommand, matching the spec's `shepherd run --workflow ...` pattern.
- **CliParams data class**: Kept picocli annotations isolated in `AppMain.kt` CLI classes. The core `ShepherdInitializer` receives a clean `CliParams` data class.
- **iterationMax**: Parsed by picocli but not yet consumed downstream. The `TicketShepherdCreatorImpl` TODOs for production wiring will eventually use this value.
- **ServerStarter abstraction**: Avoids starting real Ktor servers in unit tests. The production `KtorServerStarter` is an object (stateless).
