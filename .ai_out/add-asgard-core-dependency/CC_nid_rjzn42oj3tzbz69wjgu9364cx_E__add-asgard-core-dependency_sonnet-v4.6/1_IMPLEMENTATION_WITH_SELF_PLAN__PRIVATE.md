# Implementation PRIVATE Notes

## Task
Add asgard core dependency to this Kotlin CLI project and use Shell Runner (ProcessRunner) to call `echo` from the main App.

## Status: COMPLETE

## Steps Completed

- [x] Step 1: Attempted partial composite build (`asgard-composite/`) → FAILED due to cascade deps
- [x] Step 2: Switched to full kotlin-mp composite build → SUCCESS
- [x] Step 3: Update root `settings.gradle.kts` with `includeBuild("submodules/thorg-root/source/libraries/kotlin-mp")`
- [x] Step 4: Update `app/build.gradle.kts` with asgardCore + coroutines dependencies
- [x] Step 5: Update `App.kt` with ProcessRunner usage
- [x] Step 6: Verified build and run work

## What Was Tried

### Attempt 1: Custom asgard-composite directory
Created `asgard-composite/settings.gradle.kts` that only included `asgardCoreShared`, `asgardCoreNodeJS`, `asgardCore`.

**Failed with**: `Project with path ':asgard-composite:asgardTestTools' could not be found.`

Root cause: `buildlogic.kotlin-multiplatform` plugin unconditionally adds `implementation(project(":asgardTestTools"))` to jvmTest. This cascades to `asgardMock`, `asgardCoreJVM` etc.

### Attempt 2: publishToMavenLocal
Tried `./gradlew :asgardCore:publishToMavenLocal` on submodule's gradlew.

**Failed**: asgardCore doesn't have `maven-publish` plugin - no publishToMavenLocal task.

### Attempt 3: Include full kotlin-mp as composite (SUCCESS)
Used `includeBuild("submodules/thorg-root/source/libraries/kotlin-mp")` in root settings.

This works because:
- The kotlin-mp dir has its own `settings.gradle.kts` making it a self-contained project
- All project references like `:asgardTestTools` resolve within the composite
- Gradle 9.2.1 correctly uses the parent's Gradle version for the composite (not the 8.12 in the submodule)

## Final State
- `settings.gradle.kts`: Includes kotlin-mp composite with dependency substitution for asgardCore, asgardCoreShared, asgardCoreNodeJS
- `app/build.gradle.kts`: Has asgardCore:1.0.0 and kotlinx-coroutines-core:1.10.2 dependencies
- `App.kt`: Uses ProcessRunner.standard(outFactory).runProcess("echo", "Hello from AsgardCore ProcessRunner!")
- `asgard-composite/` directory: Was created and then removed (not needed)

## Build Command Required
```bash
THORG_ROOT=/Users/nkondrat/vintrin-env/submodules/thorg-root ./gradlew :app:build
```
