package com.glassthought.shepherd.core.agent.contextwindow

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.facade.ContextWindowState

/**
 * Reads the current context window state for an agent session.
 *
 * ap.ufavF1Ztk6vm74dLAgANY.E
 *
 * @see ContextWindowState
 */
@AnchorPoint("ap.ufavF1Ztk6vm74dLAgANY.E")
fun interface ContextWindowStateReader {

    /**
     * Reads the current context window state for an agent session.
     *
     * @throws ContextWindowStateUnavailableException if the state file
     *   is not present — this is a hard stop failure indicating the
     *   external hook is not configured.
     *
     * @return [ContextWindowState] with [ContextWindowState.remainingPercentage] = null
     *   when the file is present but its `fileUpdatedTimestamp` is older
     *   than [com.glassthought.shepherd.core.data.HarnessTimeoutConfig.contextFileStaleTimeout].
     *   Callers MUST treat null as "unknown" — no compaction should be triggered,
     *   but a warning must be logged.
     */
    suspend fun read(agentSessionId: String): ContextWindowState
}
