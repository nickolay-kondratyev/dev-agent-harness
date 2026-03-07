package org.example

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory

/**
 * Represents a tmux session that has been created.
 *
 * @param sessionName The name used to identify the session in tmux.
 */
data class TmuxSession(val sessionName: String)

/**
 * Manages the lifecycle of tmux sessions: creation, existence checks, and cleanup.
 *
 * Delegates tmux command execution to [TmuxCommandRunner].
 */
class TmuxSessionManager(
    outFactory: OutFactory,
    private val commandRunner: TmuxCommandRunner,
) {
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

        val exitCode = commandRunner.run("new-session", "-d", "-s", sessionName, command)
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

        val exitCode = commandRunner.run("kill-session", "-t", session.sessionName)
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

        val exitCode = commandRunner.run("has-session", "-t", sessionName)
        return exitCode == 0
    }
}
