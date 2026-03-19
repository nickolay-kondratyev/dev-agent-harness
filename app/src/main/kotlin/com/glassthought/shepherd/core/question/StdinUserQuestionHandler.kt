package com.glassthought.shepherd.core.question

import com.glassthought.shepherd.core.infra.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * V1 stdin/stdout implementation of [UserQuestionHandler].
 *
 * Prints the question context to [writer] and reads a multi-line answer from [reader].
 * Input is terminated by an empty line (two consecutive newlines / pressing Enter twice).
 *
 * Blocks indefinitely waiting for human input — no timeout. This is intentional:
 * the workflow pauses and resumes when the human returns.
 *
 * Constructor accepts [BufferedReader] and [PrintWriter] for testability;
 * defaults to stdin/stdout for production use.
 *
 * Uses [DispatcherProvider.io] for suspend-friendly blocking I/O.
 *
 * Spec: ref.ap.NE4puAzULta4xlOLh5kfD.E
 */
class StdinUserQuestionHandler(
    private val reader: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    private val writer: PrintWriter = PrintWriter(System.out, true),
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.standard(),
) : UserQuestionHandler {

    override suspend fun handleQuestion(context: UserQuestionContext): String =
        withContext(dispatcherProvider.io()) {
            printQuestionPrompt(context)
            readMultiLineAnswer()
        }

    private fun printQuestionPrompt(context: UserQuestionContext) {
        writer.println(DOUBLE_LINE)
        writer.println("  AGENT QUESTION")
        writer.println("  Part: ${context.partName} | Sub-part: ${context.subPartName} (${context.subPartRole})")
        writer.println("  HandshakeGuid: ${context.handshakeGuid}")
        writer.println(DOUBLE_LINE)
        writer.println()
        writer.println(context.question)
        writer.println()
        writer.println(SINGLE_LINE)
        writer.println("  Your answer (press Enter twice to submit):")
        writer.flush()
    }

    /**
     * Reads lines until an empty line is encountered (two consecutive newlines).
     * Returns all non-empty lines joined with newline separators.
     */
    private fun readMultiLineAnswer(): String =
        generateSequence { reader.readLine() }
            .takeWhile { it.isNotEmpty() }
            .joinToString(separator = "\n")

    companion object {
        private const val LINE_WIDTH = 63

        // [Unicode box-drawing]: double-line horizontal bar for prominent section headers
        private val DOUBLE_LINE = "\u2550".repeat(LINE_WIDTH)

        // [Unicode box-drawing]: single-line horizontal bar for secondary separators
        private val SINGLE_LINE = "\u2500".repeat(LINE_WIDTH)
    }
}
