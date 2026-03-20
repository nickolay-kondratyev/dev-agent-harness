package com.glassthought.shepherd.core.agent.adapter

import java.io.File

/**
 * Validated path to the directory containing callback scripts (e.g., `callback_shepherd.signal.sh`).
 *
 * Construction validates that:
 * 1. The directory exists
 * 2. The required `callback_shepherd.signal.sh` script exists in it
 * 3. The script is executable
 *
 * Use [validated] for production wiring. Use [forTest] in unit tests where the directory
 * does not need to exist on disk.
 *
 * @property path Absolute path to the callback scripts directory.
 */
class CallbackScriptsDir private constructor(
    val path: String,
) {
    companion object {
        private const val REQUIRED_SCRIPT = "callback_shepherd.signal.sh"

        /**
         * Production factory — validates that [dirPath] is an existing directory containing
         * an executable [REQUIRED_SCRIPT].
         *
         * @throws IllegalStateException if validation fails.
         */
        fun validated(dirPath: String): CallbackScriptsDir {
            val dir = File(dirPath)

            check(dir.isDirectory) {
                "Callback scripts directory does not exist or is not a directory: [$dirPath]"
            }

            val script = File(dir, REQUIRED_SCRIPT)
            check(script.exists()) {
                "$REQUIRED_SCRIPT not found in callback scripts directory [$dirPath]"
            }

            check(script.canExecute()) {
                "$REQUIRED_SCRIPT in [$dirPath] is not executable"
            }

            return CallbackScriptsDir(dirPath)
        }

        /**
         * Test factory — skips filesystem validation. Use only in unit tests where
         * the callback scripts directory does not need to exist on disk.
         */
        fun forTest(dirPath: String): CallbackScriptsDir = CallbackScriptsDir(dirPath)
    }

    override fun toString(): String = "CallbackScriptsDir(path=$path)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallbackScriptsDir) return false
        return path == other.path
    }

    override fun hashCode(): Int = path.hashCode()
}
