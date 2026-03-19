package com.glassthought.shepherd.core.server

import com.asgard.core.exception.base.AsgardBaseException

/**
 * Thrown when all retry attempts to deliver a payload and receive an ACK are exhausted.
 *
 * This means the agent is alive (TMUX session exists) but unable to process input —
 * functionally equivalent to a crash. The caller should treat this as [AgentSignal.Crashed].
 *
 * See ref.ap.r0us6iYsIRzrqHA5MVO0Q.E for the Payload Delivery ACK Protocol.
 */
class PayloadAckTimeoutException(
    message: String,
) : AsgardBaseException(message)
