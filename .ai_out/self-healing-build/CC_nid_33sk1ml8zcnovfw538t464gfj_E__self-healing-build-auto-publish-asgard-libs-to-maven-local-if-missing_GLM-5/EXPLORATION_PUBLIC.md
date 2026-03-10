# Exploration Report: Gradle Setup for Asgard Libraries

## Executive Summary

The current setup requires manual publication of asgard libraries via `export THORG_ROOT=$PWD/submodules/thorg-root && ./gradlew publishAsgardToMavenLocal` before building the chainsaw app. The proposed self-healing mechanism should automatically detect missing asgard libraries and publish them on-demand.

## Current Architecture

### 1. Root `build.gradle.kts` - Task Definitions

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/build.gradle.kts`

#### Task: `publishAsgardToMavenLocal`

```kotlin
/**
 * Publishes all asgard libraries required by chainsaw to maven local.
 *
 * Requires THORG_ROOT to be set:
 *   export THORG_ROOT=$PWD/submodules/thorg-root
 *
 * Delegates to the publishAsgardLibsToMavenLocal task in the kotlin-mp submodule.
 * This task is the one-stop command for setting up the local dev environment.
 *
 * @AnchorPoint("ap.MtB03DtelNNjPmY0VjKHs.E")
 */
tasks.register("publishAsgardToMavenLocal") {
    group = "publishing"
    description = "Publishes asgard libraries to maven local. Requires THORG_ROOT to be set."

    // Not compatible with configuration cache: reads THORG_ROOT env var and spawns a subprocess.
    notCompatibleWithConfigurationCache("reads env vars and spawns a subprocess at execution time")

    doLast {
        val thorgRoot = System.getenv("THORG_ROOT")
            ?: throw GradleException(
                "THORG_ROOT is not set. Set it before running this task:\n" +
                "  export THORG_ROOT=\$PWD/submodules/thorg-root"
            )

        // Resolve path relative to project dir using Java File (not Gradle file()) to avoid
        // configuration cache issues with Gradle project object references.
        val kotlinMpDir = java.io.File(project.projectDir, "submodules/thorg-root/source/libraries/kotlin-mp")

        if (!kotlinMpDir.exists()) {
            throw GradleException(
                "Submodule not initialized. Run: git submodule update --init"
            )
        }

        val processBuilder = ProcessBuilder("./gradlew", "publishAsgardLibsToMavenLocal")
            .directory(kotlinMpDir)
            .also { it.environment()["THORG_ROOT"] = thorgRoot }
            .inheritIO()
        val exitCode = processBuilder.start().waitFor()
        if (exitCode != 0) {
            throw GradleException("publishAsgardLibsToMavenLocal failed with exit code $exitCode")
        }
    }
}
```

#### Task: `checkAsgardInMavenLocal`

```kotlin
/**
 * Checks whether asgard libraries are present in maven local (~/.m2).
 * Fails with a GradleException if any required artifacts are missing,
 * consistent with Gradle check task convention (checkstyle, detekt, etc.).
 *
 * @AnchorPoint("ap.luMV9nN9bCUVxYfZkAVYR.E")
 */
tasks.register("checkAsgardInMavenLocal") {
    group = "publishing"
    description = "Checks whether asgard libraries are present in maven local (~/.m2). Fails if missing."

    // Not compatible with configuration cache: reads user.home system property at execution time.
    notCompatibleWithConfigurationCache("Accesses system properties at execution time.")

    doLast {
        val m2 = java.io.File(System.getProperty("user.home"), ".m2/repository/com/asgard")
        val requiredArtifacts = listOf("asgardCore", "asgardTestTools")

        val missing = requiredArtifacts.filter { artifact ->
            !m2.resolve("$artifact/1.0.0").exists()
        }

        if (missing.isEmpty()) {
            println("asgard libraries are present in maven local.")
        } else {
            throw GradleException(
                "Missing asgard libraries in maven local: $missing\n" +
                "Run: export THORG_ROOT=\$PWD/submodules/thorg-root && ./gradlew publishAsgardToMavenLocal"
            )
        }
    }
}
```

### 2. `app/build.gradle.kts` - Dependencies

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/build.gradle.kts`

```kotlin
repositories {
    // asgard libraries are published to maven local via publishAsgardToMavenLocal task.
    // mavenLocal() first: consistent with buildlogic convention and prevents remote
    // artifacts from shadowing locally published ones.
    mavenLocal()
    mavenCentral()
}

dependencies {
    // asgardTestTools provides AsgardDescribeSpec (Kotest DescribeSpec extension).
    // Kotest deps (assertions, runner) are 'implementation' in asgardTestTools (not api),
    // so we declare them directly here for compilation visibility.
    testImplementation(libs.asgard.test.tools)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)

    // AsgardCore: general-purpose foundation library (ProcessRunner, Out logging, etc.)
    implementation(libs.asgard.core)

    // Coroutines: required for runBlocking in main() and suspend ProcessRunner calls
    implementation(libs.kotlinx.coroutines.core)

    // OkHttp: HTTP client for direct LLM API calls
    implementation(libs.okhttp)

    // org.json: lightweight JSON construction and parsing for LLM API request/response bodies
    implementation(libs.json)

    // snakeyaml: YAML parsing for ticket and role markdown frontmatter
    implementation(libs.snakeyaml)

    // Jackson: structured JSON deserialization for workflow definition files
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    // Ktor: embedded HTTP server for agent-to-harness communication.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.jackson)

    // MockWebServer: local HTTP server for unit testing OkHttp-based API callers
    testImplementation(libs.mockwebserver)
}
```

### 3. THORG_ROOT Environment Variable

**Current Usage Pattern:**
```bash
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew publishAsgardToMavenLocal
```

**Requirement:** THORG_ROOT is required at runtime to access:
- Submodule path: `submodules/thorg-root/source/libraries/kotlin-mp`
- Build scripts
- asgard libraries source code

**Implementation Note:**
The `publishAsgardToMavenLocal` task uses `ProcessBuilder` to spawn a subprocess that executes `publishAsgardLibsToMavenLocal` in the kotlin-mp directory, passing THORG_ROOT as an environment variable.

### 4. Asgard Libraries - What Gets Published

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/submodules/thorg-root/source/libraries/kotlin-mp/thorgKotlinMP.build.gradle.kts`

**Published Libraries (lines 356-370):**
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

**Library List:**
1. `asgardBuildConfig` - Included build: `asgardIncludedBuild/asgardBuildConfig`
2. `asgardCoreShared` - Kotlin MP library
3. `asgardCoreNodeJS` - Kotlin MP library with NodeJS support
4. `asgardCore` - Kotlin MP library (main dependency of chainsaw)
5. `asgardTestTools` - Kotlin MP library (test dependency of chainsaw)
6. `asgardCoreJVM` - Kotlin JVM module (part of asgardTestTools)

**Version:** All libraries are versioned `1.0.0` (from line 161 in thorgKotlinMP.build.gradle.kts)

### 5. `submodules/thorg-root` - Structure Verification

**Path:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/submodules/thorg-root`

**Verification Status:** EXISTS and properly initialized

**Key Directories:**
- `source/libraries/kotlin-mp/` - Main build location
  - `thorgKotlinMP.build.gradle.kts` - Root build file
  - `asgardIncludedBuild/` - Included Gradle build
    - `asgardBuildConfig/asgardBuildConfig.build.gradle.kts`
  - `kotlin-jvm/` - Kotlin JVM modules
    - `asgardCoreJVM/`
    - `thorgDevCli/`
    - `thorgServer/`

### 6. Maven Local Artifacts

**Current Status:** MISSING (as expected - this is why self-healing is needed)

**Target Location:** `~/.m2/repository/com/asgard/`
- `asgardCore/1.0.0/asgardCore-1.0.0.pom`
- `asgardCore/1.0.0/asgardCore-1.0.0.jar`
- `asgardTestTools/1.0.0/asgardTestTools-1.0.0.pom`
- `asgardTestTools/1.0.0/asgardTestTools-1.0.0.jar`

**Expected Artifacts:**
- `asgardCore` - General-purpose foundation library
- `asgardTestTools` - Provides AsgardDescribeSpec for Kotest tests

**Note:** The `checkAsgardInMavenLocal` task only checks for these two artifacts. Additional transitive dependencies (asgardBuildConfig, asgardCoreShared, asgardCoreNodeJS, asgardCoreJVM) are not directly required for chainsaw to compile but are part of the complete asgard library set.

### 7. Dependencies Configuration

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/gradle/libs.versions.toml`

```toml
asgard = "1.0.0"

[libraries]
asgard-test-tools = { module = "com.asgard:asgardTestTools", version.ref = "asgard" }
asgard-core = { module = "com.asgard:asgardCore", version.ref = "asgard" }
```

**Resolution Pattern:**
- Uses Gradle Version Catalog for dependency management
- Dependencies are declared as `implementation(libs.asgard.core)` and `testImplementation(libs.asgard.test.tools)`
- Maven local repository is checked FIRST (line 20 in app/build.gradle.kts)

### 8. Build Registration

**app/build.gradle.kts line 78-88:**
```kotlin
tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()

    // Gradle property -PrunIntegTests=true enables integration tests.
    // Registered as a task input so Gradle cache is invalidated when it changes
    // (unlike env vars, which Gradle does not track).
    val runIntegTests = project.findProperty("runIntegTests")?.toString() == "true"
    inputs.property("runIntegTests", runIntegTests)
    systemProperty("runIntegTests", runIntegTests)
}
```

**Potential Task Dependency:**
The `test` task in `app/build.gradle.kts` would be the ideal place to add a task dependency on `ensureAsgardInMavenLocal`.

## Recommended Approach for `ensureAsgardInMavenLocal`

### Approach 1: Task Dependency on `test` (RECOMMENDED)

**Location:** `app/build.gradle.kts`

```kotlin
tasks.named<Test>("test") {
    useJUnitPlatform()
    
    // Add beforeTest action to check asgard libraries before each test
    beforeTest { descriptor ->
        // Only check if asgard artifacts are actually needed (this build)
        if (descriptor.name != "AsgardDescribeSpec" && descriptor.name != "AsgardDescribeSpecExtension") {
            // Run ensureAsgardInMavenLocal check
            val ensureTask = project.tasks.getByName("ensureAsgardInMavenLocal")
            ensureTask.get()
        }
    }
}
```

**Pros:**
- Checks only when tests run (efficient)
- Clear error message if missing
- Doesn't interfere with regular builds

**Cons:**
- Only triggers on test execution
- Error appears during test phase, not build phase

### Approach 2: Task Dependency on `build` (ALTERNATIVE)

**Location:** `app/build.gradle.kts`

```kotlin
tasks.named("build") {
    // Add dependency on ensureAsgardInMavenLocal
    dependsOn("ensureAsgardInMavenLocal")
}
```

**Pros:**
- Checks on every build (more thorough)
- Fail fast during build phase

**Cons:**
- Slower for normal builds without tests
- Might be too aggressive for developer workflows

### Implementation Recommendation

**Choose Approach 1 (Task Dependency on `test`)** with these modifications:

1. **Add new task `ensureAsgardInMavenLocal`** to `build.gradle.kts` (root):
   - Mirrors `checkAsgardInMavenLocal` but with self-healing behavior
   - Checks if libraries are present
   - If missing: automatically invokes `publishAsgardToMavenLocal`
   - If publish fails: throw exception with clear error message

2. **Add task dependency** to `app/build.gradle.kts`:
   ```kotlin
   tasks.named<Test>("test") {
       // ... existing configuration
       
       // Add dependency before any test execution
       dependsOn("ensureAsgardInMavenLocal")
   }
   ```

3. **Handle THORG_ROOT in ensureAsgardInMavenLocal**:
   - Check if THORG_ROOT is set (with helpful error message)
   - Set default if not set (or fail with clear guidance)
   - Or, require THORG_ROOT for this task (explicit about dependencies)

### Alternative: Custom Gradle Plugin

For more robust implementation, create a custom plugin in `buildSrc/`:

```kotlin
// buildSrc/src/main/kotlin/com/glassthought/chainsaw/AsgardDependenciesPlugin.kt
class AsgardDependenciesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register ensureAsgardInMavenLocal task
        // Add task dependencies to test, check, build lifecycle
    }
}
```

This approach:
- Encapsulates all asgard-related logic
- Provides better error messages and logging
- Easier to test and maintain
- Follows Gradle plugin conventions

## Files to Modify

1. **`build.gradle.kts` (root)**:
   - Add `ensureAsgardInMavenLocal` task
   - Provide clear error messages
   - Optional: Add helpful documentation

2. **`app/build.gradle.kts`**:
   - Add task dependency from `test` to `ensureAsgardInMavenLocal`
   - Or integrate into `check` task if that's preferred

## Testing Strategy

1. **Test with missing libraries**:
   ```bash
   # Remove asgard libraries from maven local
   rm -rf ~/.m2/repository/com/asgard/
   
   # Run build - should auto-publish
   ./gradlew :app:test
   
   # Verify libraries are now present
   ./gradlew checkAsgardInMavenLocal
   ```

2. **Test with existing libraries**:
   ```bash
   # Ensure libraries are present
   ./gradlew publishAsgardToMavenLocal
   
   # Run build - should pass without publishing
   ./gradlew :app:test
   ```

3. **Test THORG_ROOT not set**:
   ```bash
   # Run with THORG_ROOT not set - should fail with clear error
   unset THORG_ROOT
   ./gradlew :app:test
   ```

## Summary

The current setup has:
- Manual publishing workflow requiring THORG_ROOT environment variable
- Separate check task (`checkAsgardInMavenLocal`) that fails without helpful guidance
- Direct dependency declarations in `app/build.gradle.kts`
- Proper repository ordering (mavenLocal before mavenCentral)

The recommended implementation adds a self-healing task that:
- Automatically checks for missing asgard libraries before tests
- Auto-publishes if libraries are missing
- Provides clear error messages
- Maintains backward compatibility with existing workflow
