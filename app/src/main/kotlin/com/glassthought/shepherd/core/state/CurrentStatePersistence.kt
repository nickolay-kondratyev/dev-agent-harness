package com.glassthought.shepherd.core.state

import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists [CurrentState] to disk as JSON.
 *
 * Each [flush] is a full file rewrite using atomic write (temp file + rename) to prevent
 * corruption. The target path is determined by [AiOutputStructure.currentStateJson].
 *
 * No component reads `current_state.json` from disk during a run — the file is for
 * durability/observability only. ref.ap.K3vNzHqR8wYm5pJdL2fXa.E
 *
 * ap.RII1274uoKRv8UrOq06CD.E
 */
fun interface CurrentStatePersistence {
    suspend fun flush(state: CurrentState)
}

class CurrentStatePersistenceImpl(
    private val aiOutputStructure: AiOutputStructure,
) : CurrentStatePersistence {

    private val mapper = ShepherdObjectMapper.create()

    override suspend fun flush(state: CurrentState) {
        val targetPath = aiOutputStructure.currentStateJson()
        val parentDir = targetPath.parent

        // Ensure parent directory exists
        Files.createDirectories(parentDir)

        // Atomic write: write to temp file in same directory, then rename
        val tempFile = Files.createTempFile(parentDir, "current_state_", ".tmp")
        try {
            Files.writeString(tempFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state))
            Files.move(
                tempFile,
                targetPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: java.io.IOException) {
            // Clean up temp file on failure
            Files.deleteIfExists(tempFile)
            throw e
        }
    }
}
