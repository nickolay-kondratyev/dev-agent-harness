package com.glassthought.shepherd.core.agent

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId

/**
 * Pairs a live tmux session handle with the resolved agent session identity.
 *
 * The [tmuxSession] allows sending further instructions; [resumableAgentSessionId]
 * enables session resumption after a harness restart.
 */
@AnchorPoint("ap.DAwDPidjM0HMClPDSldXt.E")
data class TmuxAgentSession(
    val tmuxSession: TmuxSession,
    val resumableAgentSessionId: ResumableAgentSessionId,
)
