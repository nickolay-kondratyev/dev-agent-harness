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
