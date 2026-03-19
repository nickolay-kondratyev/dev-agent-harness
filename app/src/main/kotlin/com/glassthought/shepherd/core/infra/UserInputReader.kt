package com.glassthought.shepherd.core.infra

/**
 * Abstraction over stdin reading for testability.
 *
 * Production code uses [DefaultUserInputReader]; tests substitute a fake
 * that returns preset values instead of blocking on stdin.
 */
fun interface UserInputReader {
    suspend fun readLine(): String?
}

/** Delegates to [kotlin.io.readlnOrNull] for real stdin reading. */
class DefaultUserInputReader : UserInputReader {

    override suspend fun readLine(): String? = readlnOrNull()
}
