package org.example

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends keystrokes and text to an existing tmux session.
 *
 * Uses [ProcessBuilder] to invoke `tmux send-keys`. All operations are suspend functions
 * that run on [Dispatchers.IO] to avoid blocking the coroutine dispatcher.
 */
class TmuxCommunicator(outFactory: OutFactory) {
    private val out = outFactory.getOutForClass(TmuxCommunicator::class)

    /**
     * Sends text followed by the Enter key to the given tmux session.
     *
     * @param session The target tmux session.
     * @param text The text to type, followed by Enter.
     * @throws IllegalStateException if the send-keys command fails.
     */
    suspend fun sendKeys(session: TmuxSession, text: String) {
        out.info(
            "sending_keys_to_tmux_session",
            Val(session.sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(text, ValType.SHELL_COMMAND),
        )

        // [-l]: send text literally so words like "Space", "Enter", "Escape" are NOT
        // interpreted as tmux key names.
        val literalExitCode = runTmuxCommand("send-keys", "-t", session.sessionName, "-l", text)
        if (literalExitCode != 0) {
            throw IllegalStateException(
                "Failed to send literal keys to tmux session [${session.sessionName}]. Exit code: [${literalExitCode}]"
            )
        }

        // Send Enter as a separate command (NOT literal — we want the actual key press).
        val enterExitCode = runTmuxCommand("send-keys", "-t", session.sessionName, "Enter")
        if (enterExitCode != 0) {
            throw IllegalStateException(
                "Failed to send Enter to tmux session [${session.sessionName}]. Exit code: [${enterExitCode}]"
            )
        }
    }

    /**
     * Sends raw keys to the given tmux session without appending Enter.
     *
     * Useful for sending special keys (e.g., "C-c", "Escape") or partial text.
     *
     * @param session The target tmux session.
     * @param keys The raw keys to send.
     * @throws IllegalStateException if the send-keys command fails.
     */
    suspend fun sendRawKeys(session: TmuxSession, keys: String) {
        out.info(
            "sending_raw_keys_to_tmux_session",
            Val(session.sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(keys, ValType.SHELL_COMMAND),
        )

        val exitCode = runTmuxCommand("send-keys", "-t", session.sessionName, keys)
        if (exitCode != 0) {
            throw IllegalStateException(
                "Failed to send raw keys to tmux session [${session.sessionName}]. Exit code: [${exitCode}]"
            )
        }
    }

    /**
     * Executes a tmux command and returns its exit code.
     *
     * Runs on [Dispatchers.IO] to avoid blocking the coroutine dispatcher.
     */
    private suspend fun runTmuxCommand(vararg args: String): Int {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder("tmux", *args)
                .redirectErrorStream(true)
                .start()
            // Drain stdout to prevent process from blocking on full buffer.
            process.inputStream.readBytes()
            process.waitFor()
        }
    }
}
