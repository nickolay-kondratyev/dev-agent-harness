# Exploration: Remove thorg-root Submodule & Switch to Local Maven

**Date:** 2026-03-10
**Ticket:** nid_0h5gb1m47hyo0ljxb7v432q2k_E (Remove thorg-submodule)
**Status:** Understanding current state for phase 3 (switch to maven, clean up THORG_ROOT references)

---

## 1. Current Dependency Structure

### Asgard Libraries (from kotlin-mp)
All are **version 1.0.0**, packaged as **com.asgard** group:

| Library | File | Type | Notes |
|---------|------|------|-------|
| **asgardCore** | `asgardCore/asgardCore.build.gradle.kts` | Kotlin Multiplatform (JVM target) | Core foundation (ProcessRunner, Out logging, OutFactory) |
| **asgardCoreShared** | `asgardCoreShared/` | Kotlin Multiplatform (JVM + JS targets) | Transitive dep of asgardCore |
| **asgardCoreNodeJS** | `asgardCoreNodeJS/` | Kotlin Multiplatform (JVM + NodeJS targets) | Transitive dep of asgardCore |
| **asgardTestTools** | `asgardTestTools/` | Kotlin Multiplatform (JVM target) | Kotest DescribeSpec extension + testing utilities |
| **asgardBuildConfig** | `asgardIncludedBuild/asgardBuildConfig/` | Kotlin Multiplatform | Build-time configuration, marked as api() in buildlogic |

### Current app/build.gradle.kts Dependencies

```kotlin
implementation("com.asgard:asgardCore:1.0.0")
testImplementation("com.asgard:asgardTestTools:1.0.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
```

---

## 2. Composite Build Setup (Current)

### Root settings.gradle.kts

```kotlin
includeBuild("submodules/thorg-root/source/libraries/kotlin-mp") {
    dependencySubstitution {
        substitute(module("com.asgard:asgardCore")).using(project(":asgardCore"))
        substitute(module("com.asgard:asgardCoreShared")).using(project(":asgardCoreShared"))
        substitute(module("com.asgard:asgardCoreNodeJS")).using(project(":asgardCoreNodeJS"))
        substitute(module("com.asgard:asgardTestTools")).using(project(":asgardTestTools"))
    }
}
```

This makes Gradle resolve asgard libraries directly from source in the kotlin-mp project instead of from Maven. The substitution is necessary because these are transitive dependencies of asgardCore.

---

## 3. THORG_ROOT Environment Variable Dependencies

### Files that Reference THORG_ROOT

| Location | Usage | Required By |
|----------|-------|-------------|
| `settings.gradle.kts` (kotlin-mp) | Line 4: `System.getenv("THORG_ROOT") ?: throw RuntimeException(...)` | kotlin-mp build initialization |
| `thorgKotlinMP.build.gradle.kts` | Line 14-16: `System.getenv("THORG_ROOT")` for building | Root build file needs it for test run markers path (line 346) |
| `asgardBuildConfig/asgardBuildConfig.build.gradle.kts` | Line 1: Same pattern | asgardBuildConfig initialization |
| `ai_input/memory/auto_load/0_env-requirement.md` | Documents THORG_ROOT requirement | Developer documentation |
| `CLAUDE.md` | Section "Environment Prerequisites" | Project instructions |

### Why THORG_ROOT is Required

1. **kotlin-mp/settings.gradle.kts (line 7)**: `apply(from = file("$THORG_ROOT/source/libraries/kotlin-mp/gradle-includes/includeSubprojects.gradle.kts"))`
   - Loads the subproject inclusion script that discovers asgardCore, asgardCoreShared, etc.
   - This is a **critical dependency** for the build to work

2. **buildlogic configuration**: build-logic plugins also reference THORG_ROOT implicitly through the settings.gradle.kts inheritance

3. **Test run markers verification**: thorgKotlinMP.build.gradle.kts uses THORG_ROOT to locate test marker directories

---

## 4. Build Logic Configuration

### Repository Configuration

**buildlogic.kotlin-multiplatform.gradle.kts** (lines 26-29):
```kotlin
repositories {
  mavenLocal()    // <-- Already configured!
  mavenCentral()
  gradlePluginPortal()
}
```

**buildlogic.kotlin-jvm.gradle.kts**: Also has `mavenLocal()` configured

**Key Finding:** `mavenLocal()` is ALREADY present in the build repos. This means publishing and consuming from local maven is already partially supported infrastructure-wise.

---

## 5. Publishing Configuration Status

### Current State: NOT Fully Configured

1. **asgardBuildConfig** has `id("maven-publish")` plugin applied
   - This enables publishing capability
   - BUT NO explicit `publishToMavenLocal` tasks are registered in individual library build files

2. **thorgKotlinMP.build.gradle.kts** (lines 337-340):
   ```kotlin
   tasks.withType<PublishToMavenLocal> {
     enabled = false
     dependsOn(gradle.includedBuild("asgardIncludedBuild").task(":asgardBuildConfig:publishToMavenLocal"))
   }
   ```
   - **Disables** the root project's own publishToMavenLocal
   - **Depends on** asgardBuildConfig:publishToMavenLocal
   - BUT the individual library projects (asgardCore, asgardTestTools, etc.) likely don't have explicit publish tasks configured yet

3. **No explicit library-level publish tasks**: The buildlogic plugins don't currently register publishToMavenLocal tasks for multiplatform libraries

### What's Missing

To publish asgard libraries to local maven, we need:

1. **Publisher plugin configuration** in buildlogic (likely in `buildlogic.kotlin-multiplatform.gradle.kts`)
2. **Publication definitions** (POM metadata, artifact configuration) for each library
3. **Gradle task registration** to make `publishToMavenLocal` available per library
4. **Optional: Top-level aggregate task** to publish all asgard libraries in one command

---

## 6. Version Catalog Configuration

### gradle/libs.versions.toml (kotlin-mp)

```toml
[versions]
kotlin = "2.1.20"
kotlinCoroutineCore = "1.10.2"
kotest = "5.9.1"
# ... many others ...

# Asgard and Thorg library versions
asgard-core = "1.0.0"
asgard-build-config = "1.0.0"
asgard-test-tools = "1.0.0"

[libraries]
# Referenced in buildlogic and subprojects
asgard-build-config = { group = "com.asgard", name = "asgardBuildConfig", version.ref = "asgard-build-config" }
# (others likely defined similarly)
```

**Note:** The root project (`nickolay-kondratyev_dev-agent-harness`) has its own minimal `gradle/libs.versions.toml` but doesn't include asgard references. This will need to be updated or app module will need its own catalog.

---

## 7. Kotlin-MP Library Structure

### Top-Level Modules (from thorgKotlinMP.build.gradle.kts line 70-83):

```kotlin
val subProjectLibraryNames = listOf(
  "asgardCore",
  "asgardCoreShared",
  "asgardCoreNodeJS",
  "asgardGit",
  "asgardGitDrivenTest",
  "asgardMock",
  "asgardTestTools",
  "thorgCore",
  "thorgCoreNodeJS",
  "thorgCoreShared",
  "thorgSandbox",
)
```

### Kotlin JVM Projects (line 86-90):

```kotlin
val kotlinJvmProjects = listOf(
  "kotlin-jvm:asgardCoreJVM",
  "kotlin-jvm:thorgDevCli",
  "kotlin-jvm:thorgServer"
)
```

### Build Configuration

Each library follows pattern:
- `{name}/{name}.build.gradle.kts` (e.g., `asgardCore/asgardCore.build.gradle.kts`)
- Source structure: `src/commonMain/`, `src/jvmMain/`, `src/jsMain/`, etc.
- Plugins applied via buildlogic

---

## 8. Docker Setup (Phase 2 Status: "DONE")

**Search Result:** Only found one Dockerfile in thorg-root at:
- `submodules/thorg-root/source/tools/docker/docker-for-ai-agent/devcontainer/Dockerfile`

This suggests docker configuration for sharing m2 local maven repository between instances has been set up in thorg-root but NOT yet in the chainsaw repo. Since this is marked as "DONE", assume it's already implemented in the Docker compose setup (may need to locate actual docker-compose file or check CI/CD config).

---

## 9. Files That Reference THORG_ROOT (Outside submodules)

| File | Context | Impact |
|------|---------|--------|
| `CLAUDE.md` | Section: "Environment Prerequisites" | Documentation only |
| `ai_input/memory/auto_load/0_env-requirement.md` | Anchor point ap.MKHNCkA2bpT63NAvjCnvbvsb.E | Developer documentation |
| `_tickets/remove-thorg-submodule.md` | Ticket body (lines 44-56) | Task documentation |
| `.ai_out/add-asgard-core-dependency/` | Multiple files with THORG_ROOT references | Prior exploration outputs |
| `_change_log/2026-03-07_15-04-41Z.md` | "CLAUDE.md updated to document THORG_ROOT env var requirement" | Change log entry |

### Critical THORG_ROOT Documentation Reference

**ap.MKHNCkA2bpT63NAvjCnvbvsb.E** is the anchor point for the THORG_ROOT requirement documentation. This anchor point is used in:
- `ai_input/memory/auto_load/0_env-requirement.md`
- Cross-referenced in thorg-root's own `settings.gradle.kts` and build files

**This anchor point MUST be preserved** as per CLAUDE.md guidelines.

---

## 10. Gradle Build Lifecycle & Output Structure

### Build Output Directories

- `.ai_out/` — Agent exploration and iteration outputs
- `.out/` — Build artifacts and test results
- `.gradle/` — Gradle cache
- `.tmp/` — Temporary files

### Current Build Tasks

Key tasks in thorgKotlinMP.build.gradle.kts:
- `buildLibs` — Builds all Kotlin MP libraries
- `buildKotlinMpSkipTests` — Builds without running tests
- `compile` — Compiles JVM + JS targets
- `sanityCompile` — Compiles code and tests without building shadow jars
- `verifySelectedKotlinMpProjectsTestsRan` — Verifies test markers exist

---

## 11. Summary of Changes Required

### Phase 3 Work (Switch to Maven, Remove Composite Build)

| Category | Current State | Required Changes |
|----------|---------------|------------------|
| **Publishing Setup** | No explicit publishToMavenLocal tasks in libraries | Add maven-publish plugin + publication config to buildlogic; register publishToMavenLocal tasks |
| **Dependency Resolution** | Composite build with dependencySubstitution | Remove includeBuild; rely on mavenLocal() + Maven Central |
| **Root settings.gradle.kts** | includeBuild pointing to kotlin-mp | Remove or repurpose the includeBuild block |
| **THORG_ROOT References** | 5+ files document/use THORG_ROOT | Remove from CLAUDE.md, ai_input/memory/auto_load/, update app build to not require it |
| **gradle.properties** | Currently minimal | May need to add maven repo URLs or publish config |
| **Docker / CI** | Phase 2 "DONE" — docker m2 sharing | Verify docker-compose exists and works; document if needed |
| **Version Catalog** | gradle/libs.versions.toml exists in kotlin-mp | Root project catalog may need updates for asgard library versions |

---

## 12. Key Dependencies for Publishing

To implement publishToMavenLocal for kotlin-mp libraries, the buildlogic needs:

1. **maven-publish plugin** — Already available in Gradle, just needs to be applied
2. **Publication container configuration** — Define what gets published (source jar, javadoc jar, etc.)
3. **Signing configuration** — Optional, but recommended for maven artifacts
4. **Repository configuration** — Already has mavenLocal() configured

Example pattern from asgardBuildConfig shows the plugin is applied but not fully wired.

---

## 13. Blockers & Considerations

### Known Issues (from prior exploration)

1. **THORG_ROOT is a "silent requirement"** — Build fails without it being set, but this isn't obvious to new developers
2. **Composite build performance** — Switching to Maven will improve build times (no recompilation of asgard libraries on each build)
3. **Version management** — Must ensure version catalog references in app match the versions published to local maven

### Risk Factors

1. **Breaking change:** Removing composite build changes how transitive deps are resolved
2. **Docker env vars:** Need to ensure THORG_ROOT or equivalent is available in Docker build context
3. **CI/CD pipelines:** May have hardcoded paths or env vars that need updating

---

## 14. Files to Review/Modify

**Must modify:**
- `settings.gradle.kts` (root) — Remove/modify includeBuild
- `submodules/thorg-root/source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-multiplatform.gradle.kts` — Add maven-publish config
- `CLAUDE.md` (root) — Remove THORG_ROOT from Environment Prerequisites
- `ai_input/memory/auto_load/0_env-requirement.md` — Remove or repurpose

**May need to modify:**
- `gradle.properties` (root) — Add maven local repo path if needed
- `gradle/libs.versions.toml` (both root and kotlin-mp) — Ensure versions aligned
- Docker/CI configuration — Verify m2 sharing works without THORG_ROOT env var

**Do NOT modify:**
- Anchor point references (ap.MKHNCkA2bpT63NAvjCnvbvsb.E) — preserve but update content
- Individual library build.gradle.kts files in kotlin-mp (unless adding publishing config)

---

## 15. Next Steps for Implementation

1. **Add maven-publish configuration to buildlogic** (likely in buildlogic.kotlin-multiplatform.gradle.kts)
2. **Create a helper task in root settings or root build** to publish all asgard libraries to local maven
3. **Update root settings.gradle.kts** to remove includeBuild and add mavenLocal() if not present
4. **Update app/build.gradle.kts** to ensure dependencies are resolved from maven, not source
5. **Remove THORG_ROOT env var requirement** from documentation and build configuration
6. **Test full build cycle**: ./gradlew publishToMavenLocal (in kotlin-mp), then ./gradlew :app:build (in chainsaw repo)
7. **Update Docker/CI setup** to call maven publish task as needed

