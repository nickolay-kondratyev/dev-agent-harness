package com.glassthought.shepherd.usecase.rejectionnegotiation

import java.nio.file.Path

/**
 * Reads the text content of a feedback file.
 *
 * Extracted as an interface so [RejectionNegotiationUseCaseImpl] can be unit-tested
 * without real filesystem access.
 */
fun interface FeedbackFileReader {
    suspend fun readContent(path: Path): String
}
