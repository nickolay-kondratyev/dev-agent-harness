package com.glassthought.ticketShepherd.core.agent.starter

import com.glassthought.ticketShepherd.core.agent.data.TmuxStartCommand

/**
 * Builds the shell command used to start an agent inside a tmux session.
 *
 * Implementations are agent-type-specific (e.g., Claude Code CLI, PI agent)
 * and are pre-configured with all necessary flags via constructor injection.
 */
interface AgentStarter {

    /**
     * Returns the fully constructed shell command to launch the agent.
     *
     * The returned [TmuxStartCommand] is passed to
     * [com.glassthought.ticketShepherd.core.agent.tmux.TmuxSessionManager.createSession].
     */
    fun buildStartCommand(): TmuxStartCommand
}
