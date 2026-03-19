package com.glassthought.shepherd.usecase.rejectionnegotiation

import java.nio.file.Path

/**
 * Writes instruction content to a file and returns the file path.
 *
 * Extracted as an interface so [RejectionNegotiationUseCaseImpl] can be tested
 * without real filesystem access. The production implementation writes to a temp
 * directory; test fakes can return a fixed path.
 *
 * WHY: [ReInstructAndAwait] accepts a file path (not message content). This writer
 * bridges the gap — the use case builds message content, the writer persists it,
 * and the file path is passed to the agent facade.
 */
fun interface InstructionFileWriter {
    /**
     * Writes [content] to a file and returns the absolute path to that file.
     *
     * @param content The instruction message content to persist.
     * @param label A short label used for naming the file (e.g., "reviewer-judgment", "doer-compliance").
     * @return Absolute path to the written file.
     */
    suspend fun write(content: String, label: String): Path
}
