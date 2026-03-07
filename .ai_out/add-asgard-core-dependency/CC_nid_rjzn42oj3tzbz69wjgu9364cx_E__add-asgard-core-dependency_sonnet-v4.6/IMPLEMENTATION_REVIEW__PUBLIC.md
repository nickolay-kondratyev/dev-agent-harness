# Implementation Review: Add AsgardCore Dependency

**OVERALL**: PASS_WITH_MINOR_ISSUES

---

## Requirement Coverage

| Requirement | Status |
|---|---|
| Take dependency on `asgardCore` via composite build | Done |
| Use `ProcessRunner` (Shell Runner) to call `echo` from main App | Done |
| Build passes | Verified — `BUILD SUCCESSFUL` |
| Existing tests pass | Verified — `AppTest.appHasAGreeting` still passes |

---

## Feedback

### 1. [IMPORTANT] `OutFactory` (and `SimpleConsoleOutFactory`) is not closed — violates `AsgardCloseable` contract

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness/app/src/main/kotlin/org/example/App.kt`

`OutFactory` extends `AsgardCloseable`, which has a `suspend fun close()` lifecycle contract. `SimpleConsoleOutFactory.close()` delegates to `LogSinkSTDOUT.close()` which is currently a no-op, so there is no real resource leak *today*. However, the interface contract is clear and the `AsgardCloseable.use {}` extension exists precisely for this pattern. If the sink is ever replaced with a buffered or file-backed implementation (e.g., `RotatingLogFileJsonSink`), this will silently leak resources.

The `outFactory` must be closed. Since `runBlocking` is already in use at the `main()` entry point, the fix is straightforward using `AsgardCloseable.use`:

```kotlin
runBlocking {
    SimpleConsoleOutFactory.standard().use { outFactory ->
        val runner = ProcessRunner.standard(outFactory)
        val result = runner.runProcess("echo", "Hello from AsgardCore ProcessRunner!")
        println(result.trim())
    }
}
```

**Current code:**
```kotlin
runBlocking {
    val outFactory = SimpleConsoleOutFactory.standard()
    val runner = ProcessRunner.standard(outFactory)
    val result = runner.runProcess("echo", "Hello from AsgardCore ProcessRunner!")
    println(result.trim())
}
```

### 2. [IMPORTANT] `THORG_ROOT` env-var dependency is undocumented in `CLAUDE.md`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness/CLAUDE.md`

The build **silently requires** `THORG_ROOT` to be set in the environment. Without it, `./gradlew :app:build` will fail. The implementation doc mentions this, but `CLAUDE.md` (the project's persistent developer context) has no mention of it. Any developer cloning this repo will hit a confusing build failure with no in-repo guidance.

`CLAUDE.md` should document this prerequisite:
- That `THORG_ROOT` must be set
- What it should point to (e.g., the `submodules/thorg-root` submodule path)
- A sample: `export THORG_ROOT=$PWD/submodules/thorg-root`

### 3. [NON-BLOCKING] Coroutines version pinned explicitly rather than via version catalog

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness/app/build.gradle.kts`

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
```

The project uses a Gradle version catalog (`libs.*`) for other dependencies (guava, kotlin-jvm plugin, junit). The coroutines dependency was added with a hardcoded version string instead of following the existing version catalog pattern. This is inconsistent.

However, because the version catalog for this harness project is minimal and may not yet cover coroutines, this is acceptable for now. It should be moved to the version catalog for consistency when the catalog is next touched.

### 4. [NON-BLOCKING] `asgardCoreShared` and `asgardCoreNodeJS` substitutions are unused

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness/settings.gradle.kts`

```kotlin
substitute(module("com.asgard:asgardCoreShared")).using(project(":asgardCoreShared"))
substitute(module("com.asgard:asgardCoreNodeJS")).using(project(":asgardCoreNodeJS"))
```

These two substitutions are transitive — `asgardCore` depends on `asgardCoreShared` which depends on `asgardCoreNodeJS`. So they ARE needed for the composite build to resolve correctly, and the implementation doc confirms this was intentional. This is fine; the comment could be slightly stronger to explain they are transitive deps pulled in by `asgardCore`, but this is a documentation nicety, not an issue.

---

## Summary Assessment

The core task is implemented correctly: the composite build wires `asgardCore` from source, `ProcessRunner` is used correctly in `main()`, `runBlocking` usage is justified, and existing tests are preserved and passing.

The primary issue that should be fixed before merge is Issue 1: the `OutFactory` is not closed, violating the `AsgardCloseable` contract that the library itself defines. The `AsgardCloseable.use {}` extension exists for exactly this purpose and the fix is a one-liner restructure.

Issue 2 (undocumented `THORG_ROOT` requirement in `CLAUDE.md`) is a genuine maintenance risk — the next developer or agent picking up this project will hit a non-obvious build failure.

---

**Signal**: NEEDS_WORK

Blocking issues:
- Issue 1: Close `outFactory` using `AsgardCloseable.use {}`
- Issue 2: Document `THORG_ROOT` requirement in `CLAUDE.md`
