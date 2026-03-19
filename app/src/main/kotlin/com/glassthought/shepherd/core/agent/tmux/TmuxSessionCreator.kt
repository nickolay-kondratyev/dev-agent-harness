package com.glassthought.shepherd.core.agent.tmux

import com.glassthought.shepherd.core.agent.data.TmuxStartCommand

/**
 * Creates a new TMUX session running the specified command.
 *
 * Extracted as an interface so [com.glassthought.shepherd.usecase.spawn.SpawnTmuxAgentSessionUseCase]
 * can be unit-tested without a real [TmuxSessionManager] or tmux binary.
 *
 * Implemented by [TmuxSessionManager].
 */
@FunctionalInterface
interface TmuxSessionCreator {

    /**
     * Creates a new detached tmux session running the specified command.
     *
     * @param sessionName Unique name for the tmux session.
     * @param startCommand The typed command to run inside the tmux session.
     * @return A [TmuxSession] representing the created session.
     * @throws IllegalStateException if tmux fails to create the session.
     */
    suspend fun createSession(sessionName: String, startCommand: TmuxStartCommand): TmuxSession
}
