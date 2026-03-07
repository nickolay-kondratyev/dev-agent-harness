package com.glassthought.tmux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes tmux CLI commands and returns the exit code.
 *
 * Shared infrastructure for [TmuxSessionManager] and [TmuxCommunicator].
 * Runs commands on [Dispatchers.IO] to avoid blocking the coroutine dispatcher.
 */
class TmuxCommandRunner {

    /**
     * Runs a tmux command with the given arguments and returns the exit code.
     *
     * @param args The arguments to pass to `tmux` (e.g. `"new-session", "-d", "-s", "name"`).
     * @return The process exit code (0 = success).
     */
    suspend fun run(vararg args: String): Int {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder("tmux", *args)
                .redirectErrorStream(true)
                .start()
            // Drain stdout/stderr to prevent process from blocking on full buffer.
            process.inputStream.readBytes()
            process.waitFor()
        }
    }
}
