package com.glassthought.shepherd.core.agent.tmux.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Executes tmux CLI commands and returns a [ProcessResult].
 *
 * Shared infrastructure for [com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager] and [com.glassthought.shepherd.core.agent.tmux.TmuxCommunicatorImpl].
 * Runs commands on [Dispatchers.IO] to avoid blocking the coroutine dispatcher.
 */
class TmuxCommandRunner {

    /**
     * Runs a tmux command with the given arguments and returns the full [ProcessResult].
     *
     * Stdout and stderr are read concurrently to prevent the process from blocking on a full buffer.
     *
     * @param args The arguments to pass to `tmux` (e.g. `"new-session", "-d", "-s", "name"`).
     * @return [ProcessResult] containing exit code, stdout, and stderr.
     */
    suspend fun run(vararg args: String): ProcessResult {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder("tmux", *args).start()

            // Read stdout and stderr concurrently — sequential reads risk deadlock if one
            // buffer fills while waiting to drain the other.
            val stdOutDeferred = async { process.inputStream.readBytes().toString(Charsets.UTF_8) }
            val stdErrDeferred = async { process.errorStream.readBytes().toString(Charsets.UTF_8) }

            val stdOut = stdOutDeferred.await()
            val stdErr = stdErrDeferred.await()
            val exitCode = process.waitFor()

            ProcessResult(exitCode = exitCode, stdOut = stdOut, stdErr = stdErr)
        }
    }
}
