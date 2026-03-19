package com.glassthought.shepherd.core.question

/**
 * Pairs a question from an agent with the collected answer.
 *
 * Used by [QaAnswersFileWriter] to render the `qa_answers.md` file
 * and by [QaDrainAndDeliverUseCase] to accumulate results before batch delivery.
 */
data class QuestionAndAnswer(
    val question: String,
    val answer: String,
)
