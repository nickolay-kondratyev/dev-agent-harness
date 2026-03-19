package com.glassthought.shepherd.core.agent.tmux

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller

/**
 * Kills all tmux sessions by running `tmux kill-server`.
 *
 * Used during failure cleanup to ensure no orphaned agent sessions remain.
 *
 * Note: Tested via integration tests — this is a thin wrapper around [TmuxCommandRunner]
 * which uses [ProcessBuilder] internally.
 */
class TmuxAllSessionsKiller(
    outFactory: OutFactory,
    private val tmuxCommandRunner: TmuxCommandRunner,
) : AllSessionsKiller {

    private val out = outFactory.getOutForClass(TmuxAllSessionsKiller::class)

    override suspend fun killAllSessions() {
        out.info("killing_all_tmux_sessions")
        val result = tmuxCommandRunner.run("kill-server")
        if (result.exitCode == 0) {
            out.info("all_tmux_sessions_killed")
        } else {
            // Non-zero exit from `tmux kill-server` typically means no server was running.
            // This is expected — it means there are no sessions to kill.
            out.info(
                "tmux_server_not_running_nothing_to_kill",
                Val(result.exitCode, ValType.COUNT),
            )
        }
    }
}
