import org.gradle.api.GradleException

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
