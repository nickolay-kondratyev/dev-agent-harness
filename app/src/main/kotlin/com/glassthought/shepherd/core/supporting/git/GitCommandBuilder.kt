package com.glassthought.shepherd.core.supporting.git

import java.nio.file.Path

/**
 * Builds git CLI command arrays, optionally prepending `-C <workingDir>` to target
 * a specific repository directory.
 *
 * Eliminates duplication of the `gitCommand()` helper previously private to
 * [GitBranchManagerImpl], [CommitPerSubPart], and [WorkingTreeValidatorImpl].
 */
class GitCommandBuilder(
    private val workingDir: Path? = null,
) {

    /**
     * Builds a git command array from the given [args].
     *
     * When [workingDir] is non-null, the result is `["git", "-C", "<workingDir>", *args]`.
     * Otherwise, it is `["git", *args]`.
     */
    fun build(vararg args: String): Array<String> {
        return if (workingDir != null) {
            arrayOf("git", "-C", workingDir.toString(), *args)
        } else {
            arrayOf("git", *args)
        }
    }
}
