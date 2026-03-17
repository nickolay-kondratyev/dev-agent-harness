package com.glassthought.shepherd.core.agent.tmux

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName
import com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner

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
     * @param startCommand The typed command to run inside the tmux session.
     * @return A [TmuxSession] representing the created session.
     * @throws IllegalStateException if tmux fails to create the session.
     */
    suspend fun createSession(sessionName: String, startCommand: TmuxStartCommand): TmuxSession {
        out.info(
            "creating_tmux_session",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(startCommand.command, ValType.SHELL_COMMAND),
        )

        val result = commandRunner.run("new-session", "-d", "-s", sessionName, startCommand.command)
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "Failed to create tmux session [${sessionName}] with command [${startCommand.command}]. Exit code: [${result.exitCode}]. Stderr: [${result.stdErr}]"
            )
        }

        out.info(
            "tmux_session_created",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
        )

        // Pane target format: `{sessionName}:0.0` — window 0, pane 0.
        // Used for send-keys targeting; distinct from session name used for kill-session.
        val paneTarget = "$sessionName:0.0"

        return TmuxSession(
            name = TmuxSessionName(sessionName),
            paneTarget = paneTarget,
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

        val result = commandRunner.run("kill-session", "-t", session.name.sessionName)
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "Failed to kill tmux session [${session.name.sessionName}]. Exit code: [${result.exitCode}]. Stderr: [${result.stdErr}]"
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

        val result = commandRunner.run("has-session", "-t", sessionName)
        return result.exitCode == 0
    }
}
