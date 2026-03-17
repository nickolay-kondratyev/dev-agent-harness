package com.glassthought.shepherd.core.agent.tmux.util

/**
 * Result of a tmux CLI invocation.
 *
 * @property exitCode Process exit code (0 = success).
 * @property stdOut Captured standard output.
 * @property stdErr Captured standard error.
 */
data class ProcessResult(
    val exitCode: Int,
    val stdOut: String,
    val stdErr: String,
)

/**
 * Throws [IllegalStateException] if this result indicates failure (non-zero exit code).
 *
 * @param operation Short description of the failed operation (used in the error message).
 */
internal fun ProcessResult.orThrow(operation: String) {
    if (exitCode != 0)
        throw IllegalStateException("Failed to $operation. Exit code: [$exitCode]. Stderr: [$stdErr]")
}
