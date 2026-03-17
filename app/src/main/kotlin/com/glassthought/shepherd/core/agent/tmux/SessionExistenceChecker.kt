package com.glassthought.shepherd.core.agent.tmux

import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName

/**
 * Checks whether a named tmux session currently exists.
 *
 * Implemented by [TmuxSessionManager]. Injected into [TmuxSession] to break the
 * circular dependency while keeping the dependency visible in the type hierarchy.
 */
fun interface SessionExistenceChecker {
    suspend fun exists(sessionName: TmuxSessionName): Boolean
}
