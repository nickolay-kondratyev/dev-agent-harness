package com.glassthought.shepherd.core.agent.tmux

import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller

/**
 * Kills all tmux sessions by running `tmux kill-server`.
 *
 * Used during failure cleanup to ensure no orphaned agent sessions remain.
 */
class TmuxAllSessionsKiller(
    outFactory: OutFactory,
    private val tmuxCommandRunner: TmuxCommandRunner,
) : AllSessionsKiller {

    private val out = outFactory.getOutForClass(TmuxAllSessionsKiller::class)

    override suspend fun killAllSessions() {
        out.info("killing_all_tmux_sessions")
        tmuxCommandRunner.run("kill-server")
        out.info("all_tmux_sessions_killed")
    }
}
