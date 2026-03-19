package com.glassthought.shepherd.core.question

/**
 * Strategy interface for handling user questions from agents.
 *
 * Decouples the question-answering mechanism from the server and protocol.
 * The executor's health-aware await loop picks up queued questions and collects
 * answers via this interface.
 *
 * V1: [StdinUserQuestionHandler] — blocks on stdin.
 * V2+: autonomous / async strategies (see doc_v2/user-question-handler-future-strategies.md).
 *
 * Spec: ref.ap.NE4puAzULta4xlOLh5kfD.E
 */
interface UserQuestionHandler {
    /**
     * Handle a question from an agent. Returns the answer text.
     * May suspend indefinitely (e.g., waiting for human input).
     */
    suspend fun handleQuestion(context: UserQuestionContext): String
}
