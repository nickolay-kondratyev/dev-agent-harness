## Current state
- submodules/thorg-root is a git submodule (checked in under .gitmodules)
- THORG_ROOT is no longer required for ./gradlew :app:build (switched to maven local in nid_0h5gb1m47hyo0ljxb7v432q2k_E)
    - THORG_ROOT is still needed for ./gradlew publishAsgardToMavenLocal
    - Update build.gradle.kts: publishAsgardToMavenLocal task should resolve kotlin-mp dir via $THORG_ROOT (not via hardcoded submodules/ path)
        - Change: `val kotlinMpDir = java.io.File(thorgRoot, "source/libraries/kotlin-mp")` instead of `java.io.File(project.projectDir, "submodules/thorg-root/source/libraries/kotlin-mp")`
        if (!kotlinMpDir.exists()) {
            throw GradleException("THORG_ROOT directory not found or invalid. Ensure THORG_ROOT points to a standalone checkout of thorg-root)
        }
    }
} else {
        throw GradleException("publishAsgardLibsToMavenLocal failed with exit code $exitCode")
            }
        }
    }
} else {
    // Clean up the
        // Remove the submodule guard logic
        val thorgRoot = file("submodules/thorg-root")
        if (thorgRoot.exists()) return

        thorgRoot.listFiles()
            ?.filter { it.isDirectory && it.name != "source" }
            ?.let { excludeDirs.addAll(it) }

                val sourceDir = thorgRoot.resolve("source")
                if (sourceDir.exists()) {
                    sourceDir.listFiles()
                        ?.filter { it.isDirectory && it.name != "libraries" }
                        ?.let { excludeDirs.addAll(it) }
                    val librariesDir = sourceDir.resolve("libraries")
                    if (librariesDir.exists()) {
                        librariesDir.listFiles()
                            ?.filter { it.isDirectory && it.name != "kotlin-mp" }
                            ?.let { excludeDirs.addAll(it) }
                }
            }
        }
    }
} else {
    // Regenerate CLaude.md
    exec("./CLAUDE.generate.sh")
} else {
    throw GradleException("THORG_ROOT is not set. Set it before running this task:\n" +
                "  export THORG_ROOT=\$PWD/submodules/thorg-root"
            )
        }
    }
}
