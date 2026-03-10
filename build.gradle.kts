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
