# Implementation Iteration: Address Review Feedback

## Summary

Three issues from the review were addressed. Both blocking issues are fixed. The non-blocking issue was also fixed (it was trivial).

---

## What Was Fixed

### Blocking Issue 1: `OutFactory` not closed — violates `AsgardCloseable` contract

**File:** `app/src/main/kotlin/org/example/App.kt`

`SimpleConsoleOutFactory` implements `AsgardCloseable` (with `suspend fun close()`). The factory was being created but never closed, violating the resource lifecycle contract.

Added explicit import of `com.asgard.core.lifecycle.use` and wrapped the `outFactory` usage in `AsgardCloseable.use {}`:

```kotlin
runBlocking {
    SimpleConsoleOutFactory.standard().use { outFactory ->
        val runner = ProcessRunner.standard(outFactory)
        val result = runner.runProcess("echo", "Hello from AsgardCore ProcessRunner!")
        println(result.trim())
    }
}
```

`AsgardCloseable.use` is a `suspend inline` extension (in `AsgardCloseableExtensions.kt`) that mirrors the stdlib `Closeable.use {}` behavior — it guarantees `close()` is called even if the block throws, with suppressed exception handling. The explicit import is required because the extension is not in the same package as `AsgardCloseable` itself.

### Blocking Issue 2: `THORG_ROOT` env-var dependency undocumented

**File:** `CLAUDE.md`

Replaced the placeholder content with meaningful project documentation. Added a dedicated "Environment Prerequisites" section documenting:
- That `THORG_ROOT` is required for the build
- What it must point to (`submodules/thorg-root`)
- The exact export command to use

### Non-blocking: Clarifying comment on transitive substitutions in `settings.gradle.kts`

**File:** `settings.gradle.kts`

Added a two-line comment above the `asgardCoreShared` and `asgardCoreNodeJS` substitutions explaining they are transitive deps of `asgardCore`, not direct dependencies of this project, but must still be substituted for the composite build to resolve them from source.

---

## Build Verification

### `./gradlew :app:build`

Result: **BUILD SUCCESSFUL**

```
> Task :app:test
> Task :app:check
> Task :app:build
BUILD SUCCESSFUL in 3s
27 actionable tasks: 6 executed, 21 up-to-date
```

All existing tests (`AppTest.appHasAGreeting`) continued to pass.

### `./gradlew :app:run`

Result: **BUILD SUCCESSFUL**, app output correct:

```
Hello World!
{"level":"info",...,"message":"Running shell command","values":[{"value":"[echo, Hello from AsgardCore ProcessRunner!]","type":"SHELL_COMMAND"}],...}
Hello from AsgardCore ProcessRunner!
```

The `AsgardCloseable.use {}` wrapper did not change observable behavior (as expected — `SimpleConsoleOutFactory.close()` currently delegates to a no-op `LogSinkSTDOUT.close()`), but now correctly honors the lifecycle contract for future resource-holding implementations.

---

## What Was Left (None)

All blocking and non-blocking items from the review have been addressed.
