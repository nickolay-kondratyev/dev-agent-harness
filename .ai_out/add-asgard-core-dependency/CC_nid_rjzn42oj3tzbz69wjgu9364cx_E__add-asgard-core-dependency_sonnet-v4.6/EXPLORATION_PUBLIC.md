# Exploration: Add AsgardCore Dependency

## Project Structure

```
nickolay-kondratyev_dev-agent-harness/
├── settings.gradle.kts         # Root: rootProject.name, includes("app")
├── gradle.properties           # org.gradle.configuration-cache=true
├── app/
│   ├── build.gradle.kts       # Kotlin JVM + Application plugin, JVM 21, mainClass=org.example.AppKt
│   └── src/main/kotlin/org/example/App.kt  # Prints "Hello World!"
└── submodules/thorg-root/source/libraries/kotlin-mp/
    ├── asgardCore/                   # THE library to depend on
    ├── asgardIncludedBuild/          # Composite build config
    ├── settings.gradle.kts
    └── gradle/libs.versions.toml
```

## AsgardCore Library

- **Group:** `com.asgard`
- **ArtifactId:** `asgardCore`
- **Version:** `1.0.0`
- **Build file:** `asgardCore.build.gradle.kts` (naming convention used by includeSubprojects.gradle.kts)
- **Type:** Kotlin Multiplatform (but app is JVM-only, uses jvmMain sources)

### Key APIs in asgardCore

**ProcessRunner** (at `src/jvmMain/kotlin/com/asgard/core/processRunner/ProcessRunner.kt`):
- `suspend fun runProcess(vararg input: String?): String` — run shell command, returns stdout
- `suspend fun runProcessV2(timeout: Duration, vararg input: String?): ProcessResult`
- Factory: `ProcessRunner.standard(outFactory: OutFactory)`
- Note: ShellRunner is deprecated, ProcessRunner is the current interface

**OutFactory** (at `src/commonMain/kotlin/com/asgard/core/out/OutFactory.kt`):
- Required to create ProcessRunner
- Likely has a `noop()` or simple implementation

## How to Wire AsgardCore (Composite Build Pattern)

The thorg-root project uses `includeBuild()` with `dependencySubstitution`. Pattern from `submodules/thorg-root/source/libraries/kotlin-mp/settings.gradle.kts`:

```kotlin
includeBuild("asgardIncludedBuild") {
    dependencySubstitution {
        substitute(module("com.asgard:asgardBuildConfig")).using(project(":asgardBuildConfig"))
    }
}
```

For our root `settings.gradle.kts`, we need to wire the kotlin-mp included build so that `com.asgard:asgardCore` resolves to the local project.

## Implementation Strategy

1. **settings.gradle.kts**: Add `includeBuild` pointing to the asgardIncludedBuild (which auto-discovers asgardCore via includeSubprojects pattern)
2. **app/build.gradle.kts**: Add `implementation("com.asgard:asgardCore:1.0.0")` + coroutines dependency
3. **App.kt**: Use `ProcessRunner.standard(outFactory)` with `runBlocking` to call `echo`

## Key Files

- `submodules/thorg-root/source/libraries/kotlin-mp/asgardIncludedBuild/` — composite build config
- `submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/asgardCore.build.gradle.kts`
- `submodules/thorg-root/source/libraries/kotlin-mp/gradle-includes/includeSubprojects.gradle.kts`
- `submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/src/jvmMain/kotlin/com/asgard/core/processRunner/ProcessRunner.kt`
- `submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/src/commonMain/kotlin/com/asgard/core/out/OutFactory.kt`

## Critical Notes

- **Gradle version:** 9.2.1 (root), Kotlin 2.1.20 (in thorg-root)
- **ProcessRunner uses suspend functions** → need `runBlocking` in main()
- **THORG_ROOT env var** may be required by thorg-root build scripts
- **App module currently has NO version catalog** (`libs.*` refs won't resolve without one)
- Need to check what `OutFactory` simple/noop implementation looks like
