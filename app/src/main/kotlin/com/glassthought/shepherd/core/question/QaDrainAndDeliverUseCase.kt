package com.glassthought.shepherd.core.question

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.server.AckedPayloadSender
import com.glassthought.shepherd.core.session.SessionEntry
import java.nio.file.Path

/**
 * Drains all pending questions from a [SessionEntry]'s queue, collects answers
 * via [UserQuestionHandler], writes them to `qa_answers.md`, and delivers the
 * file path to the agent via [AckedPayloadSender].
 *
 * Key behaviors:
 * - Questions are processed **sequentially** in arrival order.
 * - After initial drain, re-checks the queue for questions that arrived during
 *   answer collection — continues until the queue is fully empty.
 * - All answers are delivered in **one batch** (single file, single payload).
 * - The file is **overwritten** on each delivery.
 */
open class QaDrainAndDeliverUseCase(
    private val userQuestionHandler: UserQuestionHandler,
    private val qaAnswersFileWriter: QaAnswersFileWriter,
    private val ackedPayloadSender: AckedPayloadSender,
    private val outFactory: OutFactory,
) {
    private val out = outFactory.getOutForClass(QaDrainAndDeliverUseCase::class)

    /**
     * Drains all pending questions from [sessionEntry], collects answers,
     * writes `qa_answers.md` to [commInDir], and delivers the path to the agent.
     */
    open suspend fun drainAndDeliver(sessionEntry: SessionEntry, commInDir: Path) {
        val collectedQAs = mutableListOf<QuestionAndAnswer>()

        // Drain-and-collect loop: keeps going until no new questions arrive
        while (true) {
            val pending: UserQuestionContext = sessionEntry.questionQueue.poll() ?: break

            out.info(
                "processing_pending_question",
                Val(collectedQAs.size + 1, ValType.COUNT),
            )

            val answer = userQuestionHandler.handleQuestion(pending)
            collectedQAs.add(QuestionAndAnswer(question = pending.question, answer = answer))
        }

        if (collectedQAs.isEmpty()) {
            out.info("no_questions_to_deliver")
            return
        }

        out.info(
            "writing_qa_answers_file",
            Val(collectedQAs.size, ValType.COUNT),
        )

        val filePath = qaAnswersFileWriter.write(collectedQAs, commInDir)

        val payloadContent = "Read QA answers at ${filePath.toAbsolutePath()}"

        out.info(
            "delivering_qa_answers_to_agent",
            Val(filePath.toAbsolutePath().toString(), ValType.FILE_PATH_STRING),
        )

        ackedPayloadSender.sendAndAwaitAck(
            tmuxSession = sessionEntry.tmuxAgentSession,
            sessionEntry = sessionEntry,
            payloadContent = payloadContent,
        )
    }

}
