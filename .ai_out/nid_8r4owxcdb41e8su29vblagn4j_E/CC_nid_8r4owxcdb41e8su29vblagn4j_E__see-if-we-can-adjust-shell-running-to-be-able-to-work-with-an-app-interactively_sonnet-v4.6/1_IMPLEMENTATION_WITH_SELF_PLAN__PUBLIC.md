# Implementation: Interactive Process Runner

## What Was Implemented

### Files Created
- `app/src/main/kotlin/org/example/InteractiveProcessRunner.kt` — New class implementing interactive process spawning via `ProcessBuilder.inheritIO()`. Includes `InteractiveProcessResult` data class.
- `app/src/test/kotlin/org/example/InteractiveProcessRunnerTest.kt` — 6 unit tests covering construction, successful exit, non-zero exit, and `InteractiveProcessResult` fields.

### Files Modified
- `app/src/main/kotlin/org/example/App.kt` — Updated `main()` to use `InteractiveProcessRunner` with `claude` instead of the old non-interactive `ProcessRunner.runProcess`.

---

## Key Design Decisions

### `ProcessBuilder.inheritIO()` — The Core Mechanism
`inheritIO()` is a single call that wires the child process stdin/stdout/stderr directly to the parent JVM's terminal file descriptors. No stream reading or buffering is needed — the OS handles data flow. The user interacts directly with the spawned process.

```kotlin
val processBuilder = ProcessBuilder(*command)
processBuilder.inheritIO()  // <-- connects terminal I/O directly
val process = processBuilder.start()
val exitCode = process.waitFor()
```

### `withContext(Dispatchers.IO)` for `waitFor()`
`process.waitFor()` is a blocking call. Using `withContext(Dispatchers.IO)` keeps it off the coroutine dispatcher thread, which is correct per Kotlin coroutine standards.

### Test Framework
The app module uses standard `kotlin-test` (not Kotest). `AsgardDescribeSpec` requires `asgardTestTools` which is not in the composite build substitutions and would need significant dependency chain additions. Using `kotlin-test` keeps the prototype simple and consistent with existing `AppTest.kt`.

Used `NoOpOutFactory.INSTANCE` from asgardCore (already on classpath) for a clean, no-output test setup.

---

## Build/Test Status

- **Compilation**: PASS (`./gradlew :app:compileKotlin`)
- **Tests**: PASS (`./gradlew :app:test`) — 7 total tests (6 new + 1 pre-existing)

Test results:
```
InteractiveProcessRunnerTest — 6 tests, 0 failures, 0 skipped
AppTest                      — 1 test,  0 failures, 0 skipped
```

---

## How to Test Manually

Run the app (requires `claude` CLI to be installed and in PATH):

```bash
./gradlew :app:run
```

This will:
1. Print "Hello World!"
2. Launch `claude` interactively — you can type prompts, use slash commands, etc.
3. When you exit `claude` (Ctrl+C or `/exit`), Kotlin prints the exit code and exits.

---

## Issues / Follow-up Items

- **TTY requirement**: `inheritIO()` works when the JVM runs in a real terminal. If stdin is piped (e.g., in some CI environments), interactive programs like `claude` may behave differently or fail to start their interactive mode.
- **Test coverage**: No test covers the actual interactive `claude` session since that requires a TTY. The non-interactive tests (`echo`, `false`) cover the core mechanism.
- **`interrupted = true` path**: The `InterruptedException` handling path (sets `interrupted = true`) cannot easily be tested without a real process interruption. It is implemented correctly but not covered by automated tests.
