package com.glassthought.shepherd.core.server

import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.session.SessionEntry

/**
 * Sends a payload to an agent's tmux session and awaits acknowledgement.
 *
 * The implementation handles send-keys delivery, ack polling, and retry logic.
 * This interface exists so consumers (e.g., [com.glassthought.shepherd.core.question.QaDrainAndDeliverUseCase])
 * can depend on the contract without coupling to tmux/ack internals.
 *
 * Implementation ticket: implement-ackedpayloadsender-wrap-send-keys-ack-await-retry
 */
fun interface AckedPayloadSender {
    suspend fun sendAndAwaitAck(
        tmuxSession: TmuxAgentSession,
        sessionEntry: SessionEntry,
        payloadContent: String,
    )
}
