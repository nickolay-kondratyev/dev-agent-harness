package org.example

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException

/**
 * Result of an interactive process session.
 *
 * @param exitCode The exit code returned by the process (0 = success).
 * @param interrupted True when the process was interrupted (e.g. via signal) rather than a clean exit.
 */
data class InteractiveProcessResult(
    val exitCode: Int,
    val interrupted: Boolean,
)

/**
 * Runs a process with its stdin/stdout/stderr wired directly to the terminal,
 * enabling fully interactive sessions (e.g. `claude` CLI, `vim`, `bash`).
 *
 * ## How it works
 * stdout/stderr are inherited from the JVM (INHERIT redirect).
 * stdin is connected to `/dev/tty` directly on Unix — bypassing whatever the JVM launcher
 * (e.g. Gradle) has done with the JVM's own stdin, which may be a pipe rather than a real TTY.
 * Programs like `claude` check `isatty(stdin)` and require a real TTY to enter interactive mode.
 *
 * ## Limitations
 * - `/dev/tty` approach is Unix-only; falls back to INHERIT on other platforms.
 * - Output cannot be captured — it flows directly to the terminal.
 */
class InteractiveProcessRunner(outFactory: OutFactory) {
    private val out = outFactory.getOutForClass(InteractiveProcessRunner::class)

    /**
     * Spawns the given command interactively, blocking until the user exits.
     *
     * stdin is connected to the real terminal device (`/dev/tty` on Unix) so that
     * interactive programs receive a proper TTY even when the JVM's own stdin is piped.
     *
     * @param command The command and its arguments (e.g. `"claude"` or `"bash", "-c", "ls"`).
     * @return [InteractiveProcessResult] with the exit code and whether the process was interrupted.
     */
    suspend fun runInteractive(vararg command: String): InteractiveProcessResult {
        out.info(
            "starting_interactive_process",
            Val(command.contentToString(), ValType.SHELL_COMMAND),
        )

        val processBuilder = ProcessBuilder(*command)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)

        // [/dev/tty]: connect child stdin directly to the terminal device rather than inheriting
        // the JVM's stdin. Gradle's :app:run task pipes the JVM's stdin (not a real TTY), so
        // `inheritIO()` alone would cause `isatty(stdin)` to return false in the child process,
        // breaking interactive programs like `claude` that require a TTY.
        // Probe first: /dev/tty exists on Unix but may not be openable when there is no
        // controlling terminal (e.g. in test runners). Fall back to INHERIT in that case.
        val devTty = java.io.File("/dev/tty")
        val devTtyUsable = devTty.exists() && try {
            FileInputStream(devTty).close()
            true
        } catch (_: IOException) {
            false
        }
        if (devTtyUsable) {
            processBuilder.redirectInput(devTty)
        } else {
            processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT)
        }

        return withContext(Dispatchers.IO) {
            val process = processBuilder.start()

            // waitFor blocks the IO thread until the interactive process exits.
            // withContext(Dispatchers.IO) avoids blocking the coroutine dispatcher.
            val exitCode = try {
                process.waitFor()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return@withContext InteractiveProcessResult(exitCode = -1, interrupted = true)
            }

            InteractiveProcessResult(exitCode = exitCode, interrupted = false)
        }
    }
}
