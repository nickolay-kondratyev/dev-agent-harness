package org.example

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a tmux session that has been created.
 *
 * @param sessionName The name used to identify the session in tmux.
 */
data class TmuxSession(val sessionName: String)

/**
 * Manages the lifecycle of tmux sessions: creation, existence checks, and cleanup.
 *
 * Uses [ProcessBuilder] to invoke tmux CLI commands. All operations are suspend functions
 * that run on [Dispatchers.IO] to avoid blocking the coroutine dispatcher.
 */
class TmuxSessionManager(outFactory: OutFactory) {
    private val out = outFactory.getOutForClass(TmuxSessionManager::class)

    /**
     * Creates a new detached tmux session running the specified command.
     *
     * @param sessionName Unique name for the tmux session.
     * @param command The command to run inside the tmux session.
     * @return A [TmuxSession] representing the created session.
     * @throws IllegalStateException if tmux fails to create the session.
     */
    suspend fun createSession(sessionName: String, command: String): TmuxSession {
        out.info(
            "creating_tmux_session",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(command, ValType.SHELL_COMMAND),
        )

        val exitCode = runTmuxCommand("new-session", "-d", "-s", sessionName, command)
        if (exitCode != 0) {
            throw IllegalStateException(
                "Failed to create tmux session [${sessionName}] with command [${command}]. Exit code: [${exitCode}]"
            )
        }

        out.info(
            "tmux_session_created",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
        )
        return TmuxSession(sessionName)
    }

    /**
     * Kills an existing tmux session.
     *
     * @param session The session to kill.
     * @throws IllegalStateException if tmux fails to kill the session.
     */
    suspend fun killSession(session: TmuxSession) {
        out.info(
            "killing_tmux_session",
            Val(session.sessionName, ValType.STRING_USER_AGNOSTIC),
        )

        val exitCode = runTmuxCommand("kill-session", "-t", session.sessionName)
        if (exitCode != 0) {
            throw IllegalStateException(
                "Failed to kill tmux session [${session.sessionName}]. Exit code: [${exitCode}]"
            )
        }

        out.info(
            "tmux_session_killed",
            Val(session.sessionName, ValType.STRING_USER_AGNOSTIC),
        )
    }

    /**
     * Checks whether a tmux session with the given name currently exists.
     *
     * @param sessionName The session name to check.
     * @return true if the session exists, false otherwise.
     */
    suspend fun sessionExists(sessionName: String): Boolean {
        out.debug("checking_tmux_session_exists") {
            listOf(Val(sessionName, ValType.STRING_USER_AGNOSTIC))
        }

        val exitCode = runTmuxCommand("has-session", "-t", sessionName)
        return exitCode == 0
    }

    /**
     * Executes a tmux command and returns its exit code.
     *
     * Runs on [Dispatchers.IO] to avoid blocking the coroutine dispatcher.
     */
    private suspend fun runTmuxCommand(vararg args: String): Int {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder("tmux", *args)
                .redirectErrorStream(true)
                .start()
            // Drain stdout to prevent process from blocking on full buffer.
            process.inputStream.readBytes()
            process.waitFor()
        }
    }
}
