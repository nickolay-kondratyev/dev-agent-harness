package com.glassthought.shepherd.core.question

import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.state.SubPartRole

/**
 * Context provided to [UserQuestionHandler] when an agent asks a question.
 *
 * Contains enough information to identify which agent asked and to display
 * meaningful context to the human operator.
 */
data class UserQuestionContext(
    val question: String,
    val partName: String,
    val subPartName: String,
    val subPartRole: SubPartRole,
    val handshakeGuid: HandshakeGuid,
)
