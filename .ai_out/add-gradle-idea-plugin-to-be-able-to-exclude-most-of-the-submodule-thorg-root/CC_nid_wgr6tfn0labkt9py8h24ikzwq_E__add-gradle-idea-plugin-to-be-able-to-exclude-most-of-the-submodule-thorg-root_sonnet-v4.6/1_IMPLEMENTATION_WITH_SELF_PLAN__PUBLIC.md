# Implementation: Add Gradle IDEA Plugin to Exclude thorg-root

## What Was Done

Created `build.gradle.kts` at the repo root to apply the Gradle `idea` plugin and configure IntelliJ IDEA
exclusion directories for `submodules/thorg-root`.

### Exclusion Logic

The exclusion operates at three levels to keep the path `source/libraries/kotlin-mp` accessible:

1. **Top-level** — Excludes all directories directly under `thorg-root/` except `source/`
2. **Under source/** — Excludes all directories under `thorg-root/source/` except `libraries/`
3. **Under libraries/** — Excludes all directories under `thorg-root/source/libraries/` except `kotlin-mp/`

This means IntelliJ will only index `submodules/thorg-root/source/libraries/kotlin-mp/` (the composite build
dependency) and exclude everything else in that large submodule.

## Files Modified

- `/build.gradle.kts` — **Created** (new file); applies `idea` plugin with exclusion configuration

## Verification

- `./gradlew tasks` shows IDEA tasks are registered: `cleanIdea`, `idea`, `openIdea`
- `./gradlew idea --dry-run` exits 0 — configuration is valid
- `./gradlew :app:build` — app compilation and jar succeed; a pre-existing test failure in `AppTest.kt`
  (references `App` class that does not exist in main sources) is unrelated to this change

## Notes

- The IDEA plugin is incompatible with Gradle's configuration cache (it uses file system APIs at
  configuration time). This is expected behavior for the `idea` plugin and does not affect builds.
- The pre-existing test failure in `AppTest.kt` (`Unresolved reference 'App'`) was present before
  this change. The test references a class `App` but `App.kt` only defines a `main()` function.
  A follow-up ticket should be created to clean up the stale test.
