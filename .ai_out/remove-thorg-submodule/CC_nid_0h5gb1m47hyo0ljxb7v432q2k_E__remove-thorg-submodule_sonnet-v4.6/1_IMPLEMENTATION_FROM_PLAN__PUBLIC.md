# Implementation Summary: Remove Composite Build, Switch to Maven Local

**Ticket:** nid_0h5gb1m47hyo0ljxb7v432q2k_E
**Date:** 2026-03-10
**Status:** SUCCESS

---

## Goal Achieved

`./gradlew :app:build` and `./gradlew :app:test` now work WITHOUT `THORG_ROOT` set.
`THORG_ROOT` is only needed for `./gradlew publishAsgardToMavenLocal`.

---

## Files Changed

### Phase 1: Submodule Changes

**File 1:** `submodules/thorg-root/source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-multiplatform.gradle.kts`
- Added `id("maven-publish")` to the plugins block.
- This causes all KMP projects using this convention plugin (asgardCoreShared, asgardCoreNodeJS, asgardCore, asgardTestTools) to get `publishToMavenLocal` tasks via KMP + maven-publish auto-integration.

**File 2:** `submodules/thorg-root/source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-jvm.gradle.kts`
- Added `id("maven-publish")` to the plugins block.
- Added an explicit `publishing { publications { create<MavenPublication>("maven") { from(components["java"]) } } }` block.
- **Deviation from plan:** The plan said to just add `id("maven-publish")`. In Gradle 9, the `maven-publish` plugin for JVM projects does NOT auto-create publications — an explicit `publishing {}` block was required. Without it, `publishToMavenLocal` was a no-op (no actions registered). This was discovered during verification when `asgardCoreJVM` was not appearing in `~/.m2` despite the task reporting `UP-TO-DATE`.
- The actual file DOES have `id("com.gradleup.shadow")` (the reviewer's correction about it being absent was wrong about reality, but the conclusion to add `maven-publish` was correct).

**File 3:** `submodules/thorg-root/source/libraries/kotlin-mp/thorgKotlinMP.build.gradle.kts`
- Added `publishAsgardLibsToMavenLocal` aggregate task after the existing `tasks.withType<PublishToMavenLocal>` block.
- Task depends on: `asgardBuildConfig` (from included build), `asgardCoreShared`, `asgardCoreNodeJS`, `asgardCore`, `asgardTestTools` (KMP via `:lib:publishToMavenLocal`), and `asgardCoreJVM` (JVM via `:kotlin-jvm:asgardCoreJVM:publishToMavenLocal`).

### Phase 2: Chainsaw Repo Changes

**File 4:** `settings.gradle.kts`
- Removed the entire `includeBuild("submodules/thorg-root/source/libraries/kotlin-mp") { ... }` block.
- File now only contains: plugins block + rootProject.name + include("app").

**File 5:** `app/build.gradle.kts`
- Added `mavenLocal()` to the repositories block with a comment explaining why.

**File 6:** `build.gradle.kts` (root)
- Added `import org.gradle.api.GradleException` at the top.
- Updated the IDEA comment (removed "composite build" language).
- Added `publishAsgardToMavenLocal` task — checks THORG_ROOT, uses `ProcessBuilder` to delegate to submodule's `./gradlew publishAsgardLibsToMavenLocal`.
- Added `checkAsgardInMavenLocal` task — reports presence/absence of artifacts without requiring THORG_ROOT.
- Both tasks marked with `notCompatibleWithConfigurationCache()` since they access env vars and system properties at execution time.
- **Deviation from plan:** Used `ProcessBuilder` instead of `exec {}` (plan's `exec {}` is not directly available in root `build.gradle.kts` task `doLast` scope). Also removed the top-level `val kotlinMpDir = file(...)` since that would cause configuration cache issues (project reference captured in closure). Instead resolved the path inside `doLast` using `java.io.File(project.projectDir, ...)`.

### Phase 3: Documentation

**File 7:** `ai_input/memory/auto_load/0_env-requirement.md`
- Updated content to reflect that THORG_ROOT is no longer required for regular builds.
- Preserved anchor point `ap.MKHNCkA2bpT63NAvjCnvbvsb.E` on the `## Environment Prerequisites` heading.
- Added section on asgard libraries in maven local and how to publish them.

**File 8:** `CLAUDE.md`
- Regenerated via `./CLAUDE.generate.sh`.
- No longer contains "composite build" or "required" for THORG_ROOT.
- Anchor point `ap.MKHNCkA2bpT63NAvjCnvbvsb.E` preserved.

---

## Verification Results

### Phase 1 Verification (submodule publish)
```
Exit code: 0
```
All artifacts present in `~/.m2/repository/com/asgard/`:
- asgardBuildConfig, asgardBuildConfig-js, asgardBuildConfig-jvm
- asgardCore, asgardCore-jvm
- asgardCoreJVM (after fixing the explicit publishing block)
- asgardCoreNodeJS, asgardCoreNodeJS-js, asgardCoreNodeJS-jvm
- asgardCoreShared, asgardCoreShared-js, asgardCoreShared-jvm
- asgardTestTools, asgardTestTools-jvm

### Phase 2 Verification (chainsaw build)
```
Build exit code: 0  (without THORG_ROOT)
Test exit code: 0   (without THORG_ROOT)
```

Additional checks:
- `checkAsgardInMavenLocal` — prints "asgard libraries are present in maven local." (exit 0, no THORG_ROOT needed)
- `publishAsgardToMavenLocal` without THORG_ROOT — fails with "THORG_ROOT is not set. Set it before running this task: export THORG_ROOT=$PWD/submodules/thorg-root"
- `publishAsgardToMavenLocal` with THORG_ROOT — exits 0 (delegates to submodule successfully)
- THORG_ROOT not mentioned anywhere in build log when running `:app:build`

---

## Deviations from Plan

1. **File 2 (`buildlogic.kotlin-jvm.gradle.kts`)**: Required adding an explicit `publishing {}` block in addition to `id("maven-publish")`. Without it, `publishToMavenLocal` had no actions and `asgardCoreJVM` was never published to `~/.m2`. This is correct Gradle 9 behavior for JVM projects (KMP auto-creates publications, JVM does not).

2. **File 6 (root `build.gradle.kts`)**: Used `ProcessBuilder` instead of `exec {}` because `exec {}` is not available as a top-level function in a non-plugin root `build.gradle.kts` `doLast` scope. Also used `notCompatibleWithConfigurationCache()` to properly handle configuration cache incompatibility rather than letting it silently discard the cache or fail.

3. **Reviewer correction about `buildlogic.kotlin-jvm.gradle.kts` not having shadow plugin**: The reviewer's corrected "Before" said shadow was NOT in the file. The actual file DOES have `id("com.gradleup.shadow")`. The correct change was to add `id("maven-publish")` alongside the existing `kotlin.jvm`, `com.gradleup.shadow`, and `detekt` plugins.

---

## Final Status: SUCCESS
