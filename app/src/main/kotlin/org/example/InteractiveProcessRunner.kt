package org.example

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Runs a process with its stdin/stdout/stderr wired directly to the JVM's terminal,
 * enabling fully interactive sessions (e.g. `claude` CLI, `vim`, `bash`).
 *
 * ## How it works
 * [ProcessBuilder.inheritIO] connects the child process I/O streams directly to the
 * parent JVM process streams (the terminal). No stream reading or buffering is needed —
 * the OS handles data flow. [Process.waitFor] blocks until the user exits the program.
 *
 * ## Limitations
 * - Requires the JVM to be running in a real TTY (not piped/redirected input).
 * - Output cannot be captured — it flows directly to the terminal.
 */
class InteractiveProcessRunner(outFactory: OutFactory) {
    private val out = outFactory.getOutForClass(InteractiveProcessRunner::class)

    /**
     * Spawns the given command interactively, blocking until the user exits.
     *
     * The child process inherits the JVM's stdin, stdout, and stderr directly,
     * so the user can interact with the spawned program as if they launched it
     * from their shell.
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

        // [inheritIO]: wires child process stdin/stdout/stderr directly to the JVM terminal.
        // This is the key that enables interactive use — no stream reading needed.
        processBuilder.inheritIO()

        return withContext(Dispatchers.IO) {
            val process = processBuilder.start()

            // waitFor blocks the IO thread until the interactive process exits.
            // Using withContext(Dispatchers.IO) avoids blocking the coroutine dispatcher.
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
