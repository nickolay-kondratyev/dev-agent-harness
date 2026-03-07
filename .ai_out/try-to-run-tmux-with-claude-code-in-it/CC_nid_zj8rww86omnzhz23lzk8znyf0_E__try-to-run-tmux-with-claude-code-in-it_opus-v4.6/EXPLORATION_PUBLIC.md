# Exploration: Tmux + Claude Code Integration

## Project Structure
- **Kotlin CLI application** built with Gradle, using `installDist` for running
- **Main entry**: `app/src/main/kotlin/org/example/App.kt` — currently runs `claude` interactively via `InteractiveProcessRunner`
- **run.sh**: builds via `./gradlew :app:installDist` then runs `./app/build/install/app/bin/app`
- **Dependencies**: asgardCore (Out logging, ProcessRunner), kotlinx-coroutines

## Key Existing Code

### App.kt
- Uses `SimpleConsoleOutFactory`, `InteractiveProcessRunner` to spawn `claude` interactively
- Wires stdin/stdout/stderr to `/dev/tty` for TTY passthrough
- Runs via `runBlocking` at main entry point

### InteractiveProcessRunner.kt
- Connects all 3 I/O streams to `/dev/tty` to enable interactive programs
- Returns `InteractiveProcessResult(exitCode, interrupted)`
- Falls back to `inheritIO()` when `/dev/tty` not usable

### Tests
- JUnit 5 with kotlin-test
- `InteractiveProcessRunnerTest` tests construction and non-interactive commands (echo, false)
- `AppTest` — simple greeting test

## No Existing Tmux Code
No tmux-related classes or infrastructure exist yet.

## Key Insight
The current `InteractiveProcessRunner` approach wires directly to terminal. The tmux approach is fundamentally different — it creates a **detached** tmux session and sends keystrokes programmatically, allowing headless agent orchestration.
