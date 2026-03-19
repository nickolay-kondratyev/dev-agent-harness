package com.glassthought.shepherd.core.infra

import kotlin.system.exitProcess

/**
 * Abstraction over process exit for testability.
 *
 * Production code uses [DefaultProcessExiter]; tests substitute a fake that
 * captures the exit code instead of terminating the JVM.
 */
fun interface ProcessExiter {
    fun exit(code: Int): Nothing
}

/** Calls [exitProcess] — the real JVM exit. */
class DefaultProcessExiter : ProcessExiter {
    override fun exit(code: Int): Nothing {
        exitProcess(code)
    }
}
