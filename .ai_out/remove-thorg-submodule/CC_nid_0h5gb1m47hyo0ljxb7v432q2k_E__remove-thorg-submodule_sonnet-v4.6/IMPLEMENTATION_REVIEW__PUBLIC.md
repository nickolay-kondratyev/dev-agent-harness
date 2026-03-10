# Implementation Review: Remove Composite Build, Switch to Maven Local

**Ticket:** nid_0h5gb1m47hyo0ljxb7v432q2k_E
**Reviewer Date:** 2026-03-10
**Sanity Check:** PASSED (./sanity_check.sh exits 0)

---

## Summary

The implementation removes the Gradle composite build pointing to `submodules/thorg-root/source/libraries/kotlin-mp` and replaces it with maven local resolution. The goal is that `./gradlew :app:build` works without `THORG_ROOT` set in the environment.

The chainsaw-side changes are correct and well-structured. However, there is one critical defect: the submodule changes that make `publishAsgardToMavenLocal` work are uncommitted within the submodule and not tracked by the parent repo's git history.

---

## CRITICAL Issues

### Submodule Changes Are Uncommitted

The three files changed inside `submodules/thorg-root` are present only in the working tree — they have not been committed to the submodule's git history, and the parent repo's submodule pointer has not been updated.

Evidence:
```
# Inside submodules/thorg-root:
git status --short
 M source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-jvm.gradle.kts
 M source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-multiplatform.gradle.kts
 M source/libraries/kotlin-mp/thorgKotlinMP.build.gradle.kts

# Parent repo:
git submodule status
 11ad401e3d74c503dd096b82473a4b52af0d14d4 submodules/thorg-root (modified content)

# The implementation commit c8edc68 does NOT include a submodule pointer update
```

The implementation commit `c8edc68` only staged the chainsaw-repo files. The submodule is still pointing at `11ad401e3` — the same commit as `main`. The three build file changes that enable `publishAsgardToMavenLocal` to work live only in the local working tree.

**Impact:** On a fresh clone with `git submodule update --init`, the submodule will be at the original commit without maven-publish support. `./gradlew publishAsgardToMavenLocal` will fail because:
- `buildlogic.kotlin-multiplatform.gradle.kts` will not apply `id("maven-publish")`, so KMP subprojects won't have `publishToMavenLocal` tasks
- `buildlogic.kotlin-jvm.gradle.kts` will not have the `publishing {}` block, so `asgardCoreJVM:publishToMavenLocal` will be a no-op
- `thorgKotlinMP.build.gradle.kts` will not have the `publishAsgardLibsToMavenLocal` task, causing `./gradlew publishAsgardLibsToMavenLocal` to fail with "task not found"

**Required fix:** Commit the three submodule file changes to the `thorg-root` submodule's git history, then update the parent repo's submodule pointer to the new commit. The parent repo commit must include the updated submodule SHA so that a fresh clone gets a working setup.

---

## IMPORTANT Issues

### mavenLocal() Ordering Is Inconsistent with Convention

In `app/build.gradle.kts`, `mavenCentral()` is listed before `mavenLocal()`. The convention in `buildlogic.kotlin-multiplatform.gradle.kts` (in the same codebase) lists `mavenLocal()` first:

```kotlin
// buildlogic.kotlin-multiplatform.gradle.kts
repositories {
  mavenLocal()
  mavenCentral()
  gradlePluginPortal()
}

// app/build.gradle.kts (new, inconsistent ordering)
repositories {
    mavenCentral()
    mavenLocal()
}
```

While functionally correct for asgard artifacts (they do not exist in mavenCentral), this is inconsistent with the established pattern in the same codebase. Per CLAUDE.md: "Favor consistency with existing patterns over one-off optimizations."

`mavenLocal()` before `mavenCentral()` is also the safer order for local development: it prevents a hypothetical remote artifact from shadowing a locally published one.

**Required fix:** Reorder to `mavenLocal()` first in `app/build.gradle.kts`.

### Anchor Point Uses Descriptive Name Instead of UUID

The new anchor point in `build.gradle.kts` uses a descriptive identifier:
```kotlin
// @AnchorPoint("anchor_point.publishAsgardToMavenLocal.E")
```

Every other anchor point in this codebase uses a UUID:
```kotlin
// App code
@AnchorPoint("ap.4JVSSyLwZXop6hWiJNYevFQX.E")
@AnchorPoint("ap.3BCYPiR792a2B8I9ZONDwmvN.E")
@AnchorPoint("ap.7sZveqPcid5z1ntmLs27UqN6.E")
```

Per CLAUDE.md: "ap.UUID.E" is the format. A descriptive name is not a valid anchor point ID — anchor points are meant to be stable identifiers that survive refactors, and a UUID guarantees uniqueness in a way a readable name does not. This is also not referenced anywhere else in the codebase, which reduces the value of the anchor point.

**Required fix:** Use `anchor_point.create` to generate a proper UUID-based anchor point, or remove the `@AnchorPoint` annotation if it serves no cross-reference purpose.

### checkAsgardInMavenLocal Does Not Fail When Artifacts Are Missing

The task named `checkAsgardInMavenLocal` always exits 0, even when the artifacts are missing. It only prints diagnostics:

```kotlin
if (missing.isEmpty()) {
    println("asgard libraries are present in maven local.")
} else {
    println("Missing asgard libraries in maven local: $missing")
    println("Run: ...")
    // No throw here - task succeeds!
}
```

In Gradle convention, tasks prefixed with `check` (e.g., `checkstyle`, `detekt`) fail when the check fails. This violates the Principle of Least Surprise. A developer running this as a pre-build gate will see exit 0 and assume the build will succeed, only to get a dependency resolution failure later.

**Required fix:** Throw `GradleException` when `missing.isNotEmpty()`. If purely informational behavior is intended, rename to `reportAsgardInMavenLocal`.

### ProcessBuilder Does Not Validate kotlinMpDir Existence

`publishAsgardToMavenLocal` resolves `kotlinMpDir` but does not check if it exists before passing it to `ProcessBuilder`:

```kotlin
val kotlinMpDir = java.io.File(project.projectDir, "submodules/thorg-root/source/libraries/kotlin-mp")

val processBuilder = ProcessBuilder("./gradlew", "publishAsgardLibsToMavenLocal")
    .directory(kotlinMpDir)  // If this dir doesn't exist, .start() throws IOException
```

If the submodule has not been initialized (common for first-time clones that haven't run `git submodule update --init`), `ProcessBuilder.start()` throws a raw `IOException` with a cryptic OS error message, not a clear `GradleException` explaining what went wrong.

**Required fix:** Add a guard before the `ProcessBuilder`:
```kotlin
if (!kotlinMpDir.exists()) {
    throw GradleException(
        "Submodule not initialized. Run: git submodule update --init"
    )
}
```

---

## Suggestions

### Hardcoded "1.0.0" in checkAsgardInMavenLocal Duplicates Version in Dependencies

`build.gradle.kts` hardcodes `"1.0.0"` in the check task:
```kotlin
!m2.resolve("$artifact/1.0.0").exists()
```

While `1.0.0` was already hardcoded in `app/build.gradle.kts` (pre-existing, not introduced here), this new usage duplicates that value. If the version ever changes, the check task would silently check the wrong version. Low priority since the version is not expected to change, but worth noting for completeness.

---

## What Is Correct

- **Settings removal**: The `includeBuild` block is cleanly removed from `settings.gradle.kts`. No residual composite build references.
- **CLAUDE.md regeneration**: Correctly updated via `./CLAUDE.generate.sh`. Anchor point `ap.MKHNCkA2bpT63NAvjCnvbvsb.E` is preserved on the `## Environment Prerequisites` heading.
- **Documentation accuracy**: `ai_input/memory/auto_load/0_env-requirement.md` correctly describes the new state. The content is accurate and helpful.
- **JVM publishing block correctness**: Adding the explicit `publishing { publications { create<MavenPublication>("maven") { from(components["java"]) } } }` block to `buildlogic.kotlin-jvm.gradle.kts` is the correct solution for Gradle 9. The `groupId`/`artifactId`/`version` are correctly inherited from `project.group`, `project.name`, and `project.version` — confirmed by inspecting the generated POM at `~/.m2/repository/com/asgard/asgardCoreJVM/1.0.0/asgardCoreJVM-1.0.0.pom`.
- **KMP publishing**: Adding `id("maven-publish")` to `buildlogic.kotlin-multiplatform.gradle.kts` is sufficient for KMP projects because the KMP plugin auto-wires publications when `maven-publish` is applied.
- **notCompatibleWithConfigurationCache()**: Correct and appropriate for both tasks. `publishAsgardToMavenLocal` reads env vars and spawns a subprocess; `checkAsgardInMavenLocal` reads system properties at execution time.
- **ProcessBuilder env inheritance**: `ProcessBuilder` inherits all parent environment variables by default, then adds `THORG_ROOT`. This means `JAVA_HOME`, `GRADLE_OPTS`, etc. are correctly forwarded to the subprocess. Sound approach.
- **Aggregate task design**: `publishAsgardLibsToMavenLocal` in `thorgKotlinMP.build.gradle.kts` correctly covers the full dependency chain: `asgardBuildConfig` → `asgardCoreShared` + `asgardCoreNodeJS` → `asgardCore` → `asgardCoreJVM` → `asgardTestTools`. All transitive artifacts required by chainsaw are included.
- **tasks.withType<PublishToMavenLocal> scope**: The pre-existing `tasks.withType<PublishToMavenLocal> { enabled = false }` in the thorgKotlinMP root build file is correctly scoped to root project tasks only. It does NOT affect the subproject `publishToMavenLocal` tasks that `publishAsgardLibsToMavenLocal` depends on.
- **Sanity check passes**: `./sanity_check.sh` exits 0.
- **Build without THORG_ROOT**: Verified — `./gradlew :app:build` succeeds without `THORG_ROOT` set.

---

## VERDICT: NEEDS_WORK

The critical issue is that the submodule changes are uncommitted. The implementation works on this developer machine only because of local working-tree state that is invisible to git. A fresh clone will not have the maven-publish configuration, making `publishAsgardToMavenLocal` fail. This is the core deliverable of the ticket and it must be persisted to version control before this can be approved.

The secondary issues (mavenLocal ordering, non-UUID anchor point, check task behavior, missing directory guard) are real but lower severity.
