package com.glassthought.ticketShepherd.core.agent

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxSession
import com.glassthought.ticketShepherd.core.agent.sessionresolver.ResumableAgentSessionId

/**
 * Pairs a live tmux session handle with the resolved agent session identity.
 *
 * Created by [com.glassthought.ticketShepherd.core.useCase.SpawnTmuxAgentSessionUseCase] after a successful GUID handshake.
 * The [tmuxSession] allows sending further instructions; [resumableAgentSessionId]
 * enables session resumption after a harness restart.
 */
@AnchorPoint("ap.DAwDPidjM0HMClPDSldXt.E")
data class TmuxAgentSession(
    val tmuxSession: TmuxSession,
    val resumableAgentSessionId: ResumableAgentSessionId,
)
