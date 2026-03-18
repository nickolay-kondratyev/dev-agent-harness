package com.glassthought.shepherd.core.agent.tmux

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner
import com.glassthought.shepherd.core.agent.tmux.util.orThrow

/**
 * Sends keystrokes and text to an existing tmux pane.
 *
 * Accepts a [paneTarget] string — either a session name (e.g., `shepherd_main_impl`) or
 * a pane address (e.g., `shepherd_main_impl:0.0`). See [TmuxSession.paneTarget].
 *
 * ## Input Corruption Prevention (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E)
 *
 * TMUX `send-keys` interprets certain strings as key names (e.g., "Space", "Enter", "Escape",
 * "C-c"). Without protection, payload content containing these words would be silently
 * corrupted — the agent would receive garbled input instead of literal text.
 *
 * **This interface prevents corruption via a two-command strategy:**
 * - [sendKeys]: uses `send-keys -l` (literal flag) for the text body, ensuring all content
 *   is delivered as-is without key-name interpretation. Enter is sent as a separate non-literal
 *   command. This is the primary delivery method for all harness→agent payloads
 *   (instruction file pointers, ACK wrappers, Q&A answers).
 * - [sendRawKeys]: sends keys WITHOUT the `-l` flag, allowing TMUX key-name interpretation.
 *   Reserved for control sequences (e.g., "C-c" for Ctrl+C interrupt during emergency
 *   compaction — ref.ap.8nwz2AHf503xwq8fKuLcl.E) and health pings.
 *
 * The Payload Delivery ACK Protocol (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) provides a second layer:
 * if the agent cannot parse the received payload (garbled delivery), it won't ACK, triggering
 * a retry. Together, literal-mode sending + ACK-based delivery confirmation eliminate the
 * class of silent input corruption failures.
 */
@AnchorPoint("ap.4cY9sc1jEQEseLgR7nDq0.E")
interface TmuxCommunicator {
    /**
     * Sends text literally followed by the Enter key to the given tmux pane target.
     *
     * Uses `send-keys -l` to prevent TMUX key-name interpretation — all text is delivered
     * as-is. Enter is sent separately without `-l` (we want the actual key press).
     *
     * This is the method used by `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E)
     * for all harness→agent content delivery.
     *
     * @param paneTarget TMUX pane address (e.g., `shepherd_main_impl:0.0`).
     * @param text The text to type literally, followed by Enter.
     * @throws IllegalStateException if the send-keys command fails.
     */
    suspend fun sendKeys(paneTarget: String, text: String)

    /**
     * Sends raw keys to the given tmux pane target without appending Enter.
     *
     * Does NOT use `-l` — TMUX key-name interpretation is active. Use this only for
     * control sequences (e.g., "C-c" for Ctrl+C) and health pings, never for content delivery.
     *
     * @param paneTarget TMUX pane address (e.g., `shepherd_main_impl:0.0`).
     * @param keys The raw keys to send (interpreted by TMUX).
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

    companion object {
        private const val SEND_KEYS = "send-keys"
    }

    override suspend fun sendKeys(paneTarget: String, text: String) {
        out.info(
            "sending_keys_to_tmux_pane",
            Val(paneTarget, ValType.STRING_USER_AGNOSTIC),
            Val(text, ValType.SHELL_COMMAND),
        )

        // [-l]: send text literally so words like "Space", "Enter", "Escape" are NOT
        // interpreted as tmux key names.
        commandRunner.run(SEND_KEYS, "-t", paneTarget, "-l", text)
            .orThrow("send literal keys to tmux pane [$paneTarget]")

        // Send Enter as a separate command (NOT literal — we want the actual key press).
        commandRunner.run(SEND_KEYS, "-t", paneTarget, "Enter")
            .orThrow("send Enter to tmux pane [$paneTarget]")
    }

    override suspend fun sendRawKeys(paneTarget: String, keys: String) {
        out.info(
            "sending_raw_keys_to_tmux_pane",
            Val(paneTarget, ValType.STRING_USER_AGNOSTIC),
            Val(keys, ValType.SHELL_COMMAND),
        )

        commandRunner.run(SEND_KEYS, "-t", paneTarget, keys)
            .orThrow("send raw keys to tmux pane [$paneTarget]")
    }
}
