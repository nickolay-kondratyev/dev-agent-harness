package com.glassthought.shepherd.core.question

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Writes a list of [QuestionAndAnswer] pairs to `qa_answers.md` in the specified directory.
 *
 * The file is overwritten on each call — no append semantics.
 * The caller ([QaDrainAndDeliverUseCase]) is responsible for resolving the target directory.
 */
interface QaAnswersFileWriter {
    /**
     * Writes `qa_answers.md` into [commInDir] containing all [qaList] pairs.
     *
     * @return the [Path] to the written file, so callers can reference it in payloads.
     */
    fun write(qaList: List<QuestionAndAnswer>, commInDir: Path): Path
}

/**
 * Default implementation of [QaAnswersFileWriter].
 *
 * Renders the markdown format specified in the agent-to-server communication protocol:
 * ```
 * ## Q&A Answers
 *
 * ### Question 1
 * > question text
 *
 * **Answer:** answer text
 * ```
 */
class QaAnswersFileWriterImpl : QaAnswersFileWriter {

    override fun write(qaList: List<QuestionAndAnswer>, commInDir: Path): Path {
        val filePath = commInDir.resolve(QA_ANSWERS_FILENAME)
        val content = renderMarkdown(qaList)
        filePath.writeText(content)
        return filePath
    }

    private fun renderMarkdown(qaList: List<QuestionAndAnswer>): String {
        val sb = StringBuilder()
        sb.appendLine("## Q&A Answers")

        qaList.forEachIndexed { index, qa ->
            sb.appendLine()
            sb.appendLine("### Question ${index + 1}")
            qa.question.lines().forEach { line ->
                sb.appendLine("> $line")
            }
            sb.appendLine()
            sb.appendLine("**Answer:** ${qa.answer}")
        }

        return sb.toString()
    }

    companion object {
        const val QA_ANSWERS_FILENAME = "qa_answers.md"
    }
}
