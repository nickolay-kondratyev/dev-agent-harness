package com.glassthought.shepherd.usecase.healthmonitoring

/**
 * Kills all active agent sessions.
 *
 * The default implementation ([com.glassthought.shepherd.core.agent.tmux.TmuxAllSessionsKiller])
 * kills the entire tmux server. Alternative implementations (e.g., fakes) can be injected for tests.
 */
interface AllSessionsKiller {
    suspend fun killAllSessions()
}
