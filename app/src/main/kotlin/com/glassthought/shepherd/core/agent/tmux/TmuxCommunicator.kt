package com.glassthought.shepherd.core.agent.tmux

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner

/**
 * Sends keystrokes and text to an existing tmux pane.
 *
 * Accepts a [paneTarget] string — either a session name (e.g., `shepherd_main_impl`) or
 * a pane address (e.g., `shepherd_main_impl:0.0`). See [TmuxSession.paneTarget].
 */
interface TmuxCommunicator {
    /**
     * Sends text followed by the Enter key to the given tmux pane target.
     *
     * @param paneTarget TMUX pane address (e.g., `shepherd_main_impl:0.0`).
     * @param text The text to type, followed by Enter.
     * @throws IllegalStateException if the send-keys command fails.
     */
    suspend fun sendKeys(paneTarget: String, text: String)

    /**
     * Sends raw keys to the given tmux pane target without appending Enter.
     *
     * Useful for sending special keys (e.g., "C-c", "Escape") or partial text.
     *
     * @param paneTarget TMUX pane address (e.g., `shepherd_main_impl:0.0`).
     * @param keys The raw keys to send.
     * @throws IllegalStateException if the send-keys command fails.
     */
    suspend fun sendRawKeys(paneTarget: String, keys: String)
}

/**
 * Sends keystrokes and text to an existing tmux pane.
 *
 * Delegates tmux command execution to [TmuxCommandRunner].
 */
@AnchorPoint("ap.3BCYPiR792a2B8I9ZONDwmvN.E")
class TmuxCommunicatorImpl(
    outFactory: OutFactory,
    private val commandRunner: TmuxCommandRunner,
) : TmuxCommunicator {
    private val out = outFactory.getOutForClass(TmuxCommunicatorImpl::class)

    override suspend fun sendKeys(paneTarget: String, text: String) {
        out.info(
            "sending_keys_to_tmux_pane",
            Val(paneTarget, ValType.STRING_USER_AGNOSTIC),
            Val(text, ValType.SHELL_COMMAND),
        )

        // [-l]: send text literally so words like "Space", "Enter", "Escape" are NOT
        // interpreted as tmux key names.
        val literalExitCode = commandRunner.run("send-keys", "-t", paneTarget, "-l", text)
        if (literalExitCode != 0) {
            throw IllegalStateException(
                "Failed to send literal keys to tmux pane [$paneTarget]. Exit code: [$literalExitCode]"
            )
        }

        // Send Enter as a separate command (NOT literal — we want the actual key press).
        val enterExitCode = commandRunner.run("send-keys", "-t", paneTarget, "Enter")
        if (enterExitCode != 0) {
            throw IllegalStateException(
                "Failed to send Enter to tmux pane [$paneTarget]. Exit code: [$enterExitCode]"
            )
        }
    }

    override suspend fun sendRawKeys(paneTarget: String, keys: String) {
        out.info(
            "sending_raw_keys_to_tmux_pane",
            Val(paneTarget, ValType.STRING_USER_AGNOSTIC),
            Val(keys, ValType.SHELL_COMMAND),
        )

        val exitCode = commandRunner.run("send-keys", "-t", paneTarget, keys)
        if (exitCode != 0) {
            throw IllegalStateException(
                "Failed to send raw keys to tmux pane [$paneTarget]. Exit code: [$exitCode]"
            )
        }
    }
}
