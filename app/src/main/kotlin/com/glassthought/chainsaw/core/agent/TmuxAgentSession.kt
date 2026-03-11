package com.glassthought.chainsaw.core.agent

import com.glassthought.chainsaw.core.tmux.TmuxSession
import com.glassthought.chainsaw.core.wingman.ResumableAgentSessionId

/**
 * Pairs a live tmux session handle with the resolved agent session identity.
 *
 * Created by [SpawnTmuxAgentSessionUseCase] after a successful GUID handshake.
 * The [tmuxSession] allows sending further instructions; [resumableAgentSessionId]
 * enables session resumption after a harness restart.
 */
data class TmuxAgentSession(
    val tmuxSession: TmuxSession,
    val resumableAgentSessionId: ResumableAgentSessionId,
)
