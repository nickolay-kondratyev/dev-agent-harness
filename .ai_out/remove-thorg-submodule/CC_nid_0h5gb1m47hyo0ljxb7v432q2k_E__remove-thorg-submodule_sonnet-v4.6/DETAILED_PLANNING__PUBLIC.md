# Detailed Implementation Plan: Remove Composite Build, Switch to Maven Local

**Ticket:** nid_0h5gb1m47hyo0ljxb7v432q2k_E
**Date:** 2026-03-10
**Goal:** Regular `./gradlew :app:build` must NOT require `THORG_ROOT`. `THORG_ROOT` is only needed for the explicit `publishAsgardToMavenLocal` task.

---

## Problem Understanding

Currently chainsaw uses a Gradle composite build (`includeBuild`) to resolve `com.asgard:*` dependencies directly from source in the kotlin-mp submodule. This forces `THORG_ROOT` to be set for every build because the kotlin-mp `settings.gradle.kts` throws immediately if `THORG_ROOT` is missing.

After this change: asgard libraries are pre-published to `~/.m2` (maven local). Chainsaw resolves them via `mavenLocal()` — no composite build, no `THORG_ROOT` needed at build time.

---

## 8 Files Changed — Ordered by Dependency

```
PHASE 1 — In submodule (must be done first; chainsaw changes depend on these working)
  File 1: build-logic/src/main/kotlin/buildlogic.kotlin-multiplatform.gradle.kts  (add maven-publish)
  File 2: build-logic/src/main/kotlin/buildlogic.kotlin-jvm.gradle.kts            (add maven-publish)
  File 3: thorgKotlinMP.build.gradle.kts                                           (add publishAsgardLibsToMavenLocal task)

PHASE 2 — In chainsaw repo (remove composite build, wire maven local)
  File 4: settings.gradle.kts                                                       (remove includeBuild block)
  File 5: app/build.gradle.kts                                                      (add mavenLocal() to repositories)
  File 6: build.gradle.kts                                                           (add publishAsgardToMavenLocal + checkAsgardInMavenLocal tasks; update IDEA comment)

PHASE 3 — Documentation
  File 7: ai_input/memory/auto_load/0_env-requirement.md                            (update THORG_ROOT role)
  File 8: CLAUDE.md                                                                  (regenerate via ./CLAUDE.generate.sh)
```

---

## Phase 1: Submodule Changes

### File 1: `buildlogic.kotlin-multiplatform.gradle.kts`

**Path:** `submodules/thorg-root/source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-multiplatform.gradle.kts`

**What:** Add `id("maven-publish")` to the plugins block.

**Why:** When KMP has `maven-publish` applied, Gradle automatically creates `publishToMavenLocal` tasks for each declared target (JVM, JS, etc.) plus a `publishKotlinMultiplatformPublicationToMavenLocal` task. No manual `publishing {}` block is needed — KMP wires it automatically.

**Before (lines 16–24):**
```kotlin
plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("org.jetbrains.kotlinx.kover")
  id("io.gitlab.arturbosch.detekt")
}
```

**After:**
```kotlin
plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("org.jetbrains.kotlinx.kover")
  id("io.gitlab.arturbosch.detekt")
  id("maven-publish")
}
```

**Acceptance Criteria:**
- After adding the plugin, running `./gradlew :asgardCore:publishToMavenLocal` from inside the kotlin-mp directory (with `THORG_ROOT` set) completes without error.
- `~/.m2/repository/com/asgard/asgardCore/1.0.0/asgardCore-jvm-1.0.0.jar` (or similar) exists.

---

### File 2: `buildlogic.kotlin-jvm.gradle.kts`

**Path:** `submodules/thorg-root/source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-jvm.gradle.kts`

**What:** Add `id("maven-publish")` to the plugins block.

**Why:** `asgardCoreJVM` uses `buildlogic.kotlin-jvm`. Without this, the JVM-only module won't get `publishToMavenLocal`.

**Before (lines 10–15):**
```kotlin
plugins {
  // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin
  id("org.jetbrains.kotlin.jvm")
  id("com.gradleup.shadow")
  id("io.gitlab.arturbosch.detekt")
}
```

**After:**
```kotlin
plugins {
  // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin
  id("org.jetbrains.kotlin.jvm")
  id("com.gradleup.shadow")
  id("io.gitlab.arturbosch.detekt")
  id("maven-publish")
}
```

**Acceptance Criteria:**
- `./gradlew :kotlin-jvm:asgardCoreJVM:publishToMavenLocal` succeeds from inside kotlin-mp directory.
- `~/.m2/repository/com/asgard/asgardCoreJVM/1.0.0/asgardCoreJVM-1.0.0.jar` exists.

---

### File 3: `thorgKotlinMP.build.gradle.kts`

**Path:** `submodules/thorg-root/source/libraries/kotlin-mp/thorgKotlinMP.build.gradle.kts`

**What:** Add a `publishAsgardLibsToMavenLocal` aggregate task that publishes all six asgard libraries (including `asgardBuildConfig` from the included build) to maven local.

**Why:** Chainsaw needs a single task entrypoint to invoke. This task lives in kotlin-mp so that the submodule owns its own publish lifecycle.

**Where to insert:** After the existing `tasks.withType<PublishToMavenLocal>` block (around line 340), before the `testRunMarkersDir` section. This is a new task — do NOT modify the existing `tasks.withType<PublishToMavenLocal>` block.

**Code to insert (after line 340):**
```kotlin
/**
 * Publishes all asgard libraries needed by chainsaw to maven local.
 *
 * Libraries published:
 *   - asgardBuildConfig (from included build asgardIncludedBuild)
 *   - asgardCoreShared  (transitive dep of asgardCore)
 *   - asgardCoreNodeJS  (transitive dep of asgardCore)
 *   - asgardCore        (direct dep of chainsaw)
 *   - asgardCoreJVM     (kotlin-jvm module, dep of asgardTestTools)
 *   - asgardTestTools   (direct test dep of chainsaw)
 *
 * Invoked by chainsaw's publishAsgardToMavenLocal task via exec.
 * THORG_ROOT is required when running this task.
 *
 * @AnchorPoint("anchor_point.publishAsgardLibsToMavenLocal.E")
 */
tasks.register("publishAsgardLibsToMavenLocal") {
    group = "publishing"
    description = "Publishes all asgard libraries required by chainsaw to maven local."

    // asgardBuildConfig lives in an included build — delegate to it
    dependsOn(gradle.includedBuild("asgardIncludedBuild").task(":asgardBuildConfig:publishToMavenLocal"))

    // KMP libraries (get publishToMavenLocal via buildlogic.kotlin-multiplatform after File 1 change)
    listOf("asgardCoreShared", "asgardCoreNodeJS", "asgardCore", "asgardTestTools").forEach { lib ->
        dependsOn(":$lib:publishToMavenLocal")
    }

    // kotlin-jvm module
    dependsOn(":kotlin-jvm:asgardCoreJVM:publishToMavenLocal")
}
```

**Important notes:**
- The existing `tasks.withType<PublishToMavenLocal> { enabled = false ... }` block disables the ROOT project's own publish tasks only — subproject `publishToMavenLocal` tasks are separate and unaffected by this. Our new task calls subproject tasks directly, so there is NO conflict.
- The `dependsOn(gradle.includedBuild(...))` pattern matches what the existing disabled block already uses for `asgardBuildConfig`.

**Acceptance Criteria:**
- `./gradlew publishAsgardLibsToMavenLocal` (with `THORG_ROOT` set, inside kotlin-mp dir) completes without error.
- All six artifacts present in `~/.m2/repository/com/asgard/`.

---

## Phase 2: Chainsaw Repo Changes

### File 4: `settings.gradle.kts`

**Path:** `settings.gradle.kts` (repo root)

**What:** Remove the entire `includeBuild(...)` block. Keep the `plugins` block and `include("app")`.

**Why:** Composite build is no longer needed. Dependencies will resolve from maven local. The comment explaining the composite build is also removed since it no longer applies.

**Before (full file):**
```kotlin
/*
 * This file was generated by the Gradle 'init' task.
 * ...
 */

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nickolay-kondratyev_dev-agent-harness"
include("app")

// Include the full kotlin-mp build from the submodule as a composite build.
// This makes com.asgard:asgardCore (and all other com.asgard/com.thorg artifacts)
// resolvable directly from source without publishing to Maven.
includeBuild("submodules/thorg-root/source/libraries/kotlin-mp") {
    dependencySubstitution {
        substitute(module("com.asgard:asgardCore")).using(project(":asgardCore"))
        // asgardCoreShared and asgardCoreNodeJS are transitive deps of asgardCore (not direct deps of this project).
        // They must be substituted here so the composite build can resolve them from source as well.
        substitute(module("com.asgard:asgardCoreShared")).using(project(":asgardCoreShared"))
        substitute(module("com.asgard:asgardCoreNodeJS")).using(project(":asgardCoreNodeJS"))
        substitute(module("com.asgard:asgardTestTools")).using(project(":asgardTestTools"))
    }
}
```

**After:**
```kotlin
/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 * For more detailed information on multi-project builds, please refer to https://docs.gradle.org/9.2.1/userguide/multi_project_builds.html in the Gradle documentation.
 */

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nickolay-kondratyev_dev-agent-harness"
include("app")
```

**Acceptance Criteria:**
- `./gradlew :app:build` (with asgard libs already in `~/.m2`) does NOT require `THORG_ROOT` and succeeds.
- `./gradlew :app:build` without `THORG_ROOT` set and without `~/.m2/com/asgard` artifacts fails with a dependency resolution error (not a `THORG_ROOT` missing error).

---

### File 5: `app/build.gradle.kts`

**Path:** `app/build.gradle.kts`

**What:** Add `mavenLocal()` to the `repositories` block.

**Why:** Currently only `mavenCentral()` is listed. Without `mavenLocal()`, Gradle will not look in `~/.m2` for `com.asgard:*` artifacts.

**Before (lines 16–19):**
```kotlin
repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}
```

**After:**
```kotlin
repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    // asgard libraries are published to maven local via publishAsgardToMavenLocal task.
    mavenLocal()
}
```

**Acceptance Criteria:**
- Gradle dependency resolution for `com.asgard:asgardCore:1.0.0` resolves from `~/.m2` (verify with `./gradlew :app:dependencies | grep asgardCore`).

---

### File 6: `build.gradle.kts`

**Path:** `build.gradle.kts` (repo root)

**What:**
1. Add `publishAsgardToMavenLocal` task — delegates to submodule via `exec`.
2. Add `checkAsgardInMavenLocal` task — reports presence/absence of artifacts, prints clear guidance.
3. Update the IDEA module comment to remove the now-outdated reference to composite build.

**Why the IDEA comment must change:** The existing comment says `// Only the path to source/libraries/kotlin-mp is kept accessible, because it is included as a composite build`. After this change, we no longer have a composite build, so the comment would be misleading. The IDEA exclusions themselves can stay (they still reduce indexing noise) but the justification comment changes.

**Before (full file):**
```kotlin
plugins {
    idea
}

// Exclude most of the thorg-root submodule from IntelliJ IDEA indexing.
// Only the path to `source/libraries/kotlin-mp` is kept accessible,
// because it is included as a composite build (see settings.gradle.kts).
idea {
    module {
        val thorgRoot = file("submodules/thorg-root")

        // Exclude all top-level dirs under thorg-root except "source"
        // (source is the parent of the libraries/kotlin-mp path we keep).
        thorgRoot.listFiles()
            ?.filter { it.isDirectory && it.name != "source" }
            ?.let { excludeDirs.addAll(it) }

        // Exclude all dirs under source/ except "libraries".
        val sourceDir = thorgRoot.resolve("source")
        if (sourceDir.exists()) {
            sourceDir.listFiles()
                ?.filter { it.isDirectory && it.name != "libraries" }
                ?.let { excludeDirs.addAll(it) }

            // Exclude all dirs under source/libraries/ except "kotlin-mp".
            val librariesDir = sourceDir.resolve("libraries")
            if (librariesDir.exists()) {
                librariesDir.listFiles()
                    ?.filter { it.isDirectory && it.name != "kotlin-mp" }
                    ?.let { excludeDirs.addAll(it) }
            }
        }
    }
}
```

**After:**
```kotlin
plugins {
    idea
}

// Exclude most of the thorg-root submodule from IntelliJ IDEA indexing.
// The submodule stays in the repo for local publishing, but most of its
// contents are irrelevant to chainsaw development and slow down indexing.
idea {
    module {
        val thorgRoot = file("submodules/thorg-root")

        // Exclude all top-level dirs under thorg-root except "source"
        // (source is the parent of the libraries/kotlin-mp path we keep for reference).
        thorgRoot.listFiles()
            ?.filter { it.isDirectory && it.name != "source" }
            ?.let { excludeDirs.addAll(it) }

        // Exclude all dirs under source/ except "libraries".
        val sourceDir = thorgRoot.resolve("source")
        if (sourceDir.exists()) {
            sourceDir.listFiles()
                ?.filter { it.isDirectory && it.name != "libraries" }
                ?.let { excludeDirs.addAll(it) }

            // Exclude all dirs under source/libraries/ except "kotlin-mp".
            val librariesDir = sourceDir.resolve("libraries")
            if (librariesDir.exists()) {
                librariesDir.listFiles()
                    ?.filter { it.isDirectory && it.name != "kotlin-mp" }
                    ?.let { excludeDirs.addAll(it) }
            }
        }
    }
}

// Path within the submodule where the kotlin-mp Gradle build lives.
// Used by publishAsgardToMavenLocal to invoke Gradle in the submodule.
val kotlinMpDir = file("submodules/thorg-root/source/libraries/kotlin-mp")

/**
 * Publishes all asgard libraries required by chainsaw to maven local.
 *
 * Requires THORG_ROOT to be set:
 *   export THORG_ROOT=$PWD/submodules/thorg-root
 *
 * Delegates to the publishAsgardLibsToMavenLocal task in the kotlin-mp submodule.
 * This task is the one-stop command for setting up the local dev environment.
 *
 * @AnchorPoint("anchor_point.publishAsgardToMavenLocal.E")
 */
tasks.register("publishAsgardToMavenLocal") {
    group = "publishing"
    description = "Publishes asgard libraries to maven local. Requires THORG_ROOT to be set."

    doLast {
        val thorgRoot = System.getenv("THORG_ROOT")
            ?: throw GradleException(
                "THORG_ROOT is not set. Set it before running this task:\n" +
                "  export THORG_ROOT=\$PWD/submodules/thorg-root"
            )

        exec {
            workingDir(kotlinMpDir)
            commandLine("./gradlew", "publishAsgardLibsToMavenLocal")
            environment("THORG_ROOT", thorgRoot)
        }
    }
}

/**
 * Checks whether asgard libraries are present in maven local.
 * Prints clear YES/NO status and guidance if missing.
 */
tasks.register("checkAsgardInMavenLocal") {
    group = "publishing"
    description = "Reports whether asgard libraries are present in maven local (~/.m2)."

    doLast {
        val m2 = file("${System.getProperty("user.home")}/.m2/repository/com/asgard")
        val requiredArtifacts = listOf("asgardCore", "asgardTestTools")

        val missing = requiredArtifacts.filter { artifact ->
            !m2.resolve("$artifact/1.0.0").exists()
        }

        if (missing.isEmpty()) {
            println("asgard libraries are present in maven local.")
        } else {
            println("Missing asgard libraries in maven local: $missing")
            println("Run: export THORG_ROOT=\$PWD/submodules/thorg-root && ./gradlew publishAsgardToMavenLocal")
        }
    }
}
```

**Note on `GradleException` import:** `GradleException` is available in buildscript scope without an import in `.gradle.kts` files — it is part of the Gradle API automatically in scope.

**Acceptance Criteria:**
- `./gradlew checkAsgardInMavenLocal` prints either "present" or "missing + guidance" without requiring `THORG_ROOT`.
- `THORG_ROOT=$PWD/submodules/thorg-root ./gradlew publishAsgardToMavenLocal` succeeds and all `~/.m2/repository/com/asgard/*/1.0.0/` directories exist.
- `./gradlew publishAsgardToMavenLocal` (without THORG_ROOT) fails with a clear error message.

---

## Phase 3: Documentation Changes

### File 7: `ai_input/memory/auto_load/0_env-requirement.md`

**Path:** `ai_input/memory/auto_load/0_env-requirement.md`

**What:** Update content to reflect that `THORG_ROOT` is no longer required for regular builds. Keep `ap.MKHNCkA2bpT63NAvjCnvbvsb.E` anchor point on the same heading line — it MUST NOT move or be removed.

**Before:**
```markdown

## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)

### `THORG_ROOT` (required)
The build depends on `THORG_ROOT` being set in the environment. Without it, `./gradlew :app:build` will fail.

`THORG_ROOT` must point to the root of the `thorg-root` submodule (checked in under `submodules/thorg-root`):

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
```

This is needed because the composite build in `settings.gradle.kts` includes
`submodules/thorg-root/source/libraries/kotlin-mp`, and that build uses `THORG_ROOT` internally
(e.g., for resolving version catalogs and sub-project paths).
```

**After:**
```markdown

## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)

### `THORG_ROOT` (only needed for publishing asgard libraries)
`THORG_ROOT` is NOT required for regular builds. `./gradlew :app:build` works without it.

`THORG_ROOT` is only required when explicitly publishing asgard libraries to maven local:

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew publishAsgardToMavenLocal
```

### Asgard Libraries in Maven Local (required for build)

`./gradlew :app:build` requires `com.asgard:asgardCore:1.0.0` and `com.asgard:asgardTestTools:1.0.0`
to be present in `~/.m2`. Check status with:

```bash
./gradlew checkAsgardInMavenLocal
```

If missing, publish them:

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew publishAsgardToMavenLocal
```
```

**Acceptance Criteria:**
- Anchor point `ap.MKHNCkA2bpT63NAvjCnvbvsb.E` remains on the `## Environment Prerequisites` heading line, unchanged.
- Content accurately describes the new workflow.

---

### File 8: `CLAUDE.md`

**Path:** `CLAUDE.md` (repo root)

**What:** Regenerate via `./CLAUDE.generate.sh`.

**Why:** `CLAUDE.md` is auto-generated from `ai_input/memory/auto_load/`. After updating `0_env-requirement.md`, regenerating ensures `CLAUDE.md` reflects the updated documentation.

**Command:**
```bash
./CLAUDE.generate.sh
```

**Acceptance Criteria:**
- `CLAUDE.md` contains the updated `THORG_ROOT` documentation (no longer "required", points to `publishAsgardToMavenLocal`).
- `CLAUDE.md` does NOT contain "The build depends on `THORG_ROOT` being set" or "composite build".

---

## Verification Steps (End-to-End)

### Step 1: Verify submodule publish works

```bash
cd submodules/thorg-root/source/libraries/kotlin-mp
export THORG_ROOT=$PWD/../../../..   # points to submodules/thorg-root
# or from repo root:
export THORG_ROOT=$PWD/submodules/thorg-root

cd submodules/thorg-root/source/libraries/kotlin-mp
./gradlew publishAsgardLibsToMavenLocal > .tmp/publish-asgard.log 2>&1
echo "Exit code: $?"
```

Check all artifacts:
```bash
ls ~/.m2/repository/com/asgard/
# Expected: asgardBuildConfig/ asgardCore/ asgardCoreJVM/ asgardCoreNodeJS/ asgardCoreShared/ asgardTestTools/
```

### Step 2: Verify chainsaw build without THORG_ROOT

```bash
cd /path/to/chainsaw-repo
unset THORG_ROOT

./gradlew :app:build > .tmp/build-no-thorgroot.log 2>&1
echo "Exit code: $?"
# Expected: 0 (success)
```

Confirm `THORG_ROOT` is not mentioned in error output:
```bash
grep -i "THORG_ROOT" .tmp/build-no-thorgroot.log
# Expected: no output (or only in success messages if any)
```

### Step 3: Verify checkAsgardInMavenLocal works without THORG_ROOT

```bash
unset THORG_ROOT
./gradlew checkAsgardInMavenLocal
# Expected: "asgard libraries are present in maven local."
```

### Step 4: Verify publishAsgardToMavenLocal fails clearly without THORG_ROOT

```bash
unset THORG_ROOT
./gradlew publishAsgardToMavenLocal
# Expected: build failure with message:
# "THORG_ROOT is not set. Set it before running this task: export THORG_ROOT=..."
```

### Step 5: Verify publishAsgardToMavenLocal works with THORG_ROOT

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew publishAsgardToMavenLocal
# Expected: success, delegates to kotlin-mp submodule
```

### Step 6: Verify tests pass

```bash
unset THORG_ROOT
./gradlew :app:test > .tmp/test-results.log 2>&1
echo "Exit code: $?"
```

---

## Key Risk: KMP Maven Publications

When `id("maven-publish")` is added to `buildlogic.kotlin-multiplatform.gradle.kts`, KMP auto-creates multiple publications per library (one per declared target plus a root "kotlin multiplatform" publication). The task `publishToMavenLocal` is an aggregate of all of these — which is exactly what we want.

**Potential issue:** KMP with JS target may generate a `publishJsPublicationToMavenLocalRepository` task that needs node/yarn. This is typically fine for local maven publishing (it publishes the JS artifacts too), but if it fails, the mitigation is to only call the JVM-specific publication task for each library: `publishJvmPublicationToMavenLocalRepository` instead of `publishToMavenLocal`. Check this when running Step 1 verification.

**Mitigation approach (if JS publish fails):** Change the task dependencies in `publishAsgardLibsToMavenLocal` from:
```kotlin
dependsOn(":$lib:publishToMavenLocal")
```
to:
```kotlin
dependsOn(":$lib:publishJvmPublicationToMavenLocal")
dependsOn(":$lib:publishKotlinMultiplatformPublicationToMavenLocal")
```
This publishes only JVM and the KMP metadata artifact — sufficient for chainsaw (which is a JVM project).

---

## Order Summary (Strict Implementation Order)

1. **File 1** — Add `maven-publish` to `buildlogic.kotlin-multiplatform.gradle.kts`
2. **File 2** — Add `maven-publish` to `buildlogic.kotlin-jvm.gradle.kts`
3. **File 3** — Add `publishAsgardLibsToMavenLocal` task to `thorgKotlinMP.build.gradle.kts`
4. **VERIFY** — Run `publishAsgardLibsToMavenLocal` from within kotlin-mp (with THORG_ROOT set). All artifacts must land in `~/.m2` before proceeding.
5. **File 4** — Remove `includeBuild` block from chainsaw `settings.gradle.kts`
6. **File 5** — Add `mavenLocal()` to `app/build.gradle.kts`
7. **VERIFY** — Run `./gradlew :app:build` (no THORG_ROOT). Must succeed.
8. **File 6** — Add `publishAsgardToMavenLocal` and `checkAsgardInMavenLocal` to root `build.gradle.kts`; update IDEA comment.
9. **File 7** — Update `ai_input/memory/auto_load/0_env-requirement.md`
10. **File 8** — Run `./CLAUDE.generate.sh` to regenerate `CLAUDE.md`
11. **FINAL VERIFY** — Run full end-to-end verification steps above.

**Do NOT remove the `includeBuild` (File 4) before `~/.m2` is populated (Step 4).** If you do, the build will break and you cannot easily get it back in state to re-publish.

---

## Testing Strategy

No new automated tests are introduced by this change (it's a build infrastructure change). Verification is through the manual steps above. If future work adds a CI bootstrap script, it should run `checkAsgardInMavenLocal` and `publishAsgardToMavenLocal` as needed.

---

## What is NOT Changed

- `submodules/thorg-root` git submodule registration — stays in `.gitmodules`, submodule stays checked out.
- Individual library `build.gradle.kts` files (`asgardCore/asgardCore.build.gradle.kts`, etc.) — untouched.
- `asgardBuildConfig/asgardBuildConfig.build.gradle.kts` — already has `maven-publish`, untouched.
- The `tasks.withType<PublishToMavenLocal> { enabled = false }` block in `thorgKotlinMP.build.gradle.kts` — untouched. It disables the ROOT project's publish tasks only and does not conflict with subproject tasks.
- ValType migration — separate ticket.
