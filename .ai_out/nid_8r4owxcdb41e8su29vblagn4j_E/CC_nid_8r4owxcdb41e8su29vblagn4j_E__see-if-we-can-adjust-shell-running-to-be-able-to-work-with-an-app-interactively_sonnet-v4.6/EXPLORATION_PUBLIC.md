# EXPLORATION_PUBLIC.md
## Interactive Shell Running - Codebase Exploration

### Key File Paths

- **ProcessRunner interface:** `submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/src/jvmMain/kotlin/com/asgard/core/processRunner/ProcessRunner.kt`
- **ProcessRunnerImpl:** `submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/src/jvmMain/kotlin/com/asgard/core/processRunner/impl/ProcessRunnerImpl.kt`
- **App entry point:** `app/src/main/kotlin/org/example/App.kt`
- **App build config:** `app/build.gradle.kts`
- **Unix integration tests:** `submodules/thorg-root/source/libraries/kotlin-mp/asgardTestTools/src/jvmTest/kotlin/com/asgard/testTools/processRunner/ProcessRunnerImplUnixyIntegTest.kt`

### Current ProcessRunner Interface

The `ProcessRunner` interface (in asgardCore submodule - DO NOT MODIFY) supports:
- `runProcess(vararg input: String?): String` - captures stdout, throws on non-zero exit
- `runProcessV2(timeout, vararg input): ProcessResult` - structured result with stdout, stderr, exitCode
- `runScript(script: File): String` - runs a script file

**Critical:** asgardCore is in submodules - we should NOT add interactive support there. We add a new abstraction in the app module.

### Current App.kt

```kotlin
fun main() {
    println(App().greeting)
    runBlocking {
        SimpleConsoleOutFactory.standard().use { outFactory ->
            val runner = ProcessRunner.standard(outFactory)
            val result = runner.runProcess("claude", "Say hello")
            println(result.trim())
            println("AFTER CLAUDE CALL BACK TO KOTLIN")
        }
    }
}
```

### Why Current Approach Fails for Interactivity

`ProcessRunner` uses `BufferedReader` to capture stdout - this prevents interactive I/O. The process's stdin is not connected to the terminal.

### Solution: New `InteractiveProcessRunner` in app module

Add to `app/src/main/kotlin/org/example/`:

**Approach:**
1. Use `ProcessBuilder.inheritIO()` to wire terminal stdin/stdout/stderr directly to the child process
2. Just `process.waitFor()` - no output buffering needed
3. Return `InteractiveProcessResult` with exit code + whether process was interrupted
4. Handle SIGINT gracefully: child gets it, JVM continues

**Key Java API:**
```kotlin
val pb = ProcessBuilder(*command)
pb.inheritIO()  // <-- This is the magic: connects terminal I/O directly
val process = pb.start()
val exitCode = process.waitFor()
```

**No stream reading needed** - inheritIO does everything.

### Architecture Decision

- **Where to add:** `app/src/main/kotlin/org/example/` (NOT in asgardCore submodule)
- **New class:** `InteractiveProcessRunner` - separate from `ProcessRunner`
- **Result type:** `InteractiveProcessResult(exitCode: Int, interrupted: Boolean)`
- **Update App.kt** to demonstrate usage with `claude`

### Build System

- Kotlin JVM, Gradle, Java 21 toolchain
- asgardCore dependency: `asgardCore:1.0.0` provides `ProcessRunner`, `Out`, `OutFactory`
- Kotest for testing (BDD style with `AsgardDescribeSpec`)
- Tests in `app/src/test/kotlin/org/example/`

### Testing Notes

- Integration tests for process running use env var `ASGARD_RUN_INTEG_TESTS=true`
- Existing tests in `AppTest.kt` use `AsgardDescribeSpec`
- Interactive runner is tricky to test (requires TTY), but we can test the class structure and non-interactive aspects
- The main verification is running `./gradlew run` and interacting with `claude`

### Logging Pattern

All logging via `Out` interface (never `println` for logging, `println` OK for user communication):
```kotlin
out.info("starting_interactive_process", Val(command.contentToString(), ValType.SHELL_COMMAND))
```
