package com.glassthought.shepherd.core.session

import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.state.SubPartRole

/**
 * Context surrounding a user question raised by an agent during execution.
 *
 * Captures the identity of the agent (via [handshakeGuid]) and its position
 * within the plan ([partName], [subPartName], [subPartRole]) so the harness
 * can route the question and its answer back correctly.
 *
 * See ref.ap.NE4puAzULta4xlOLh5kfD.E for the UserQuestionHandler spec.
 */
data class UserQuestionContext(
    val question: String,
    val partName: String,
    val subPartName: String,
    val subPartRole: SubPartRole,
    val handshakeGuid: HandshakeGuid,
)
