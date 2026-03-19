package com.glassthought.shepherd.core.infra

/**
 * Abstraction over console output for testability.
 *
 * Production code uses [DefaultConsoleOutput]; tests substitute a fake
 * that captures printed messages instead of writing to stdout.
 */
interface ConsoleOutput {
    fun printlnRed(message: String)
}

/** Prints to stdout with ANSI red color codes. */
class DefaultConsoleOutput : ConsoleOutput {

    override fun printlnRed(message: String) {
        println("$ANSI_RED$message$ANSI_RESET")
    }

    companion object {
        private const val ANSI_RED = "\u001b[31m"
        private const val ANSI_RESET = "\u001b[0m"
    }
}
