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
