package com.glassthought.chainsaw.core.agent.tmux

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.chainsaw.core.agent.tmux.data.TmuxSessionName
import com.glassthought.chainsaw.core.agent.tmux.util.TmuxCommandRunner

/**
 * Manages the lifecycle of tmux sessions: creation, existence checks, and cleanup.
 *
 * Delegates tmux command execution to [TmuxCommandRunner].
 * Session interaction (sending keys, checking existence) is available via the returned [TmuxSession].
 */
class TmuxSessionManager(
    outFactory: OutFactory,
    private val commandRunner: TmuxCommandRunner,
    private val communicator: TmuxCommunicator,
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

        return TmuxSession(
            name = TmuxSessionName(sessionName),
            communicator = communicator,
            existsChecker = { sessionExists(sessionName) },
        )
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
            Val(session.name.sessionName, ValType.STRING_USER_AGNOSTIC),
        )

        val exitCode = commandRunner.run("kill-session", "-t", session.name.sessionName)
        if (exitCode != 0) {
            throw IllegalStateException(
                "Failed to kill tmux session [${session.name.sessionName}]. Exit code: [${exitCode}]"
            )
        }

        out.info(
            "tmux_session_killed",
            Val(session.name.sessionName, ValType.STRING_USER_AGNOSTIC),
        )
    }

    /**
     * Checks whether a tmux session with the given name currently exists.
     *
     * Internal logic — exposed to callers via [TmuxSession.exists].
     *
     * @param sessionName The session name to check.
     * @return true if the session exists, false otherwise.
     */
    private suspend fun sessionExists(sessionName: String): Boolean {
        out.debug("checking_tmux_session_exists") {
            listOf(Val(sessionName, ValType.STRING_USER_AGNOSTIC))
        }

        val exitCode = commandRunner.run("has-session", "-t", sessionName)
        return exitCode == 0
    }
}
