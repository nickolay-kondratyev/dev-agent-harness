package com.glassthought.shepherd.core.session

/**
 * A question awaiting user response, paired with the full [context]
 * identifying which agent raised it and from which plan position.
 */
data class PendingQuestion(
    val question: String,
    val context: UserQuestionContext,
)
