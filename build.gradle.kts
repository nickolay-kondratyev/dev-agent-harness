import org.gradle.api.GradleException

/**
 * Publishes all asgard libraries required by chainsaw to maven local.
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
