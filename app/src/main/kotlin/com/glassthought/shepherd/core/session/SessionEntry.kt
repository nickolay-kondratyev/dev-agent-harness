package com.glassthought.shepherd.core.session

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.question.UserQuestionContext
import com.glassthought.shepherd.core.server.PayloadId
import com.glassthought.shepherd.core.state.SubPartRole
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Live registry entry for a single agent session within [SessionsState].
 *
 * Tracks the agent's tmux session handle, its position in the plan,
 * the deferred signal awaited by the executor, and any pending user questions.
 *
 * [isQAPending] and [role] are derived properties — no separate state to synchronize.
 * [questionQueue] uses [ConcurrentLinkedQueue] for thread-safe access from both
 * the server callback thread and the orchestration coroutine.
 *
 * See ref.ap.7V6upjt21tOoCFXA7nqNh.E for the SessionsState spec.
 */
@AnchorPoint("ap.igClEuLMC0bn7mDrK41jQ.E")
data class SessionEntry(
    val tmuxAgentSession: TmuxAgentSession,
    val partName: String,
    val subPartName: String,
    val subPartIndex: Int,
    val signalDeferred: CompletableDeferred<AgentSignal>,
    val lastActivityTimestamp: Instant,
    val pendingPayloadAck: PayloadId?,
    val questionQueue: ConcurrentLinkedQueue<UserQuestionContext>,
) {
    /** True when one or more user questions are awaiting a response. */
    val isQAPending: Boolean get() = questionQueue.isNotEmpty()

    /** Role derived on-the-fly from [subPartIndex] — never stored. */
    val role: SubPartRole get() = SubPartRole.fromIndex(subPartIndex)
}
