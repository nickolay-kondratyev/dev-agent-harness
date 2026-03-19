package com.glassthought.shepherd.core.supporting.git

import java.nio.file.Files
import java.nio.file.Path

/**
 * Filesystem abstraction for `.git/index.lock` operations.
 *
 * Exists to allow faking in unit tests — production code uses [StandardGitIndexLockFileOperations].
 */
interface GitIndexLockFileOperations {

    /** Returns true if `.git/index.lock` exists on disk. */
    suspend fun indexLockExists(): Boolean

    /** Deletes `.git/index.lock`. Returns true if the file was deleted, false if it did not exist. */
    suspend fun deleteIndexLock(): Boolean
}

/**
 * Production implementation backed by [java.nio.file.Files].
 *
 * @param gitDir Path to the `.git` directory (e.g., `Path.of("/repo/.git")`).
 */
class StandardGitIndexLockFileOperations(
    private val gitDir: Path,
) : GitIndexLockFileOperations {

    private val indexLockPath: Path = gitDir.resolve("index.lock")

    override suspend fun indexLockExists(): Boolean = Files.exists(indexLockPath)

    override suspend fun deleteIndexLock(): Boolean = Files.deleteIfExists(indexLockPath)
}
