import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.sonarqube)
}

// Sonar plugin is NOT compatible with Gradle configuration cache.
// Run via: ./run_sonar.sh (which passes --no-configuration-cache).
sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "nickolay-kondratyev")
        property("sonar.projectKey", "nickolay-kondratyev_dev-agent-harness")
        property("sonar.token", System.getenv("SONAR_TOKEN") ?: "")
        // Source/test paths are auto-discovered by the plugin from the Gradle project structure.
        // Setting them explicitly causes "indexed twice" errors in multi-module builds.
        property("sonar.exclusions", "**/build/**,**/.gradle/**,**/.kotlin/**,**/node_modules/**")
    }
}

// Associate coverage report with :app module where the actual sources live.
// Setting this at root level causes "File 'X.kt' not found in project sources"
// because the root project has no indexed source files.
project(":app") {
    sonar {
        properties {
            property("sonar.coverage.jacoco.xmlReportPaths", "${rootDir}/.out/coverage.xml")
        }
    }
}

// Wire coverage generation into the sonar task so `./gradlew sonar` always includes fresh coverage.
// sonar is defined in the root project; koverXmlReport lives in :app.
tasks.named("sonar") {
    dependsOn(":app:koverXmlReport")
}

/**
 * Lists all Gradle tasks from all subprojects as JSON, written to build/tasks-json/tasks.json.
 *
 * Gradle's UP-TO-DATE mechanism serves as the cache: when no build file has changed since
 * the last run, Gradle skips execution and the output file is reused by the caller.
 * This avoids brittle shell-level memoization that cannot detect when tasks actually change.
 *
 * Cache invalidation inputs:
 *   - All *.gradle.kts build files (root + subprojects)
 *   - settings.gradle.kts
 *
 * Output: build/tasks-json/tasks.json (JSON array, one object per task)
 *
 * Used by gradle_tasks_jsonl_cached.sh → gradle_run.sh.
 */
tasks.register("tasksJson") {
    group = "build"
    description = "Lists all Gradle tasks as JSON. Gradle UP-TO-DATE caching avoids redundant runs."

    val outputFile = layout.buildDirectory.file("tasks-json/tasks.json")

    // Inputs: all build files across all subprojects + settings file.
    // Gradle re-runs the task only when any of these files change.
    inputs.files(
        rootProject.allprojects.mapNotNull { proj ->
            proj.buildFile.takeIf { it.exists() }
        }
    )
    inputs.file(file("settings.gradle.kts"))

    outputs.file(outputFile)

    // Accesses the live project task graph at execution time — not configuration-cache-safe.
    notCompatibleWithConfigurationCache("Accesses project task graph at execution time.")

    doLast {
        val taskList = rootProject.allprojects.flatMap { proj ->
            proj.tasks.map { task ->
                mapOf(
                    "name" to task.name,
                    "path" to task.path,
                    "project" to proj.path,
                    "group" to (task.group ?: "other"),
                    "description" to (task.description ?: "")
                )
            }
        }

        val json = groovy.json.JsonOutput.prettyPrint(
            groovy.json.JsonOutput.toJson(taskList)
        )

        outputFile.get().asFile.also {
            it.parentFile.mkdirs()
            it.writeText(json)
        }
    }
}


/**
 * Publishes all asgard libraries required by shepherd to maven local.
 *
 * Requires THORG_ROOT to be set pointing to a standalone thorg-root checkout:
 *   export THORG_ROOT=$HOME/thorg-root
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
                "  export THORG_ROOT=\$HOME/thorg-root"
            )

        // Use THORG_ROOT directly instead of hardcoded submodule path.
        val kotlinMpDir = java.io.File(thorgRoot, "source/libraries/kotlin-mp")

        if (!kotlinMpDir.exists()) {
            throw GradleException(
                "THORG_ROOT directory not found or invalid. Ensure THORG_ROOT points to a valid thorg-root checkout.\n" +
                "  Current THORG_ROOT: $thorgRoot\n" +
                "  Expected path: \$THORG_ROOT/source/libraries/kotlin-mp"
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
                "Run: export THORG_ROOT=\$HOME/thorg-root && ./gradlew publishAsgardToMavenLocal"
            )
        }
    }
}

/**
 * Ensures asgard libraries are present in maven local (~/.m2).
 * Auto-publishes missing libraries without requiring manual THORG_ROOT setup.
 *
 * NOTE: This task cannot be wired as `dependsOn` of compileKotlin. Gradle resolves
 * maven coordinates at CONFIGURATION time — before any task runs — so if libs are
 * absent the build fails before this task can execute. Self-healing is handled by
 * _prepare_pre_build.sh (ref.ap.gtpABfFlF4RE1SITt7k1P.E), which runs before Gradle.
 *
 * This task remains useful for manual re-publishing:
 *   ./gradlew ensureAsgardInMavenLocal
 *
 * Fast path: If artifacts already exist, completes in <1s via upToDateWhen check.
 * Slow path: If missing, auto-publishes using THORG_ROOT=$HOME/thorg-root.
 *
 * @AnchorPoint("ap.VZk3hR8tJmPcXqYsNvLbW.E")
 */
tasks.register("ensureAsgardInMavenLocal") {
    group = "publishing"
    description = "Ensures asgard libraries are in maven local, auto-publishing if missing."

    // Not compatible with configuration cache: accesses system properties and spawns subprocess.
    notCompatibleWithConfigurationCache("Accesses system properties and spawns subprocess at execution time.")

    // Fast path: skip execution if artifacts already exist
    outputs.upToDateWhen {
        val m2 = java.io.File(System.getProperty("user.home"), ".m2/repository/com/asgard")
        val requiredArtifacts = listOf("asgardCore", "asgardTestTools")
        requiredArtifacts.all { artifact ->
            m2.resolve("$artifact/1.0.0").exists()
        }
    }

    doLast {
        val m2 = java.io.File(System.getProperty("user.home"), ".m2/repository/com/asgard")
        val requiredArtifacts = listOf("asgardCore", "asgardTestTools")

        val missing = requiredArtifacts.filter { artifact ->
            !m2.resolve("$artifact/1.0.0").exists()
        }

        if (missing.isEmpty()) {
            println("asgard libraries are present in maven local.")
            return@doLast
        }

        // Auto-publish missing libraries
        println("Missing asgard libraries: $missing. Auto-publishing...")

        // Auto-set THORG_ROOT pointing to standalone checkout (submodule removed).
        val thorgRoot = java.io.File(System.getProperty("user.home"), "thorg-root").absolutePath
        val kotlinMpDir = java.io.File(thorgRoot, "source/libraries/kotlin-mp")

        if (!kotlinMpDir.exists()) {
            throw GradleException(
                "THORG_ROOT directory not found or invalid. Ensure a thorg-root checkout exists at \$HOME/thorg-root.\n" +
                "  Expected path: $thorgRoot/source/libraries/kotlin-mp"
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

        println("Successfully published asgard libraries to maven local.")
    }
}
