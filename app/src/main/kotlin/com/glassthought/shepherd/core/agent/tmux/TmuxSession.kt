package com.glassthought.shepherd.core.agent.tmux

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName

/**
 * Represents a live tmux session. Acts as the primary handle for interacting with a session.
 *
 * Encapsulates the session name, pane target, key-sending capability, and existence checking.
 *
 * [paneTarget] is the TMUX pane address used for `send-keys` (e.g., `shepherd_main_impl:0.0`),
 * distinct from [name] which identifies the session for `kill-session`.
 *
 * Instances are created by [TmuxSessionManager.createSession].
 */
@AnchorPoint("ap.7sZveqPcid5z1ntmLs27UqN6.E")
class TmuxSession(
    val name: TmuxSessionName,
    /** TMUX pane target for send-keys (e.g., `shepherd_main_impl:0.0`). */
    val paneTarget: String,
    private val communicator: TmuxCommunicator,
    // The [existsChecker] lambda avoids a circular dependency with [TmuxSessionManager].
    private val existsChecker: suspend () -> Boolean,
) {
    /**
     * Sends text followed by the Enter key to this tmux session pane.
     *
     * @param text The text to type, followed by Enter.
     */
    suspend fun sendKeys(text: String) = communicator.sendKeys(paneTarget, text)

    /**
     * Sends raw keys to this tmux session pane without appending Enter.
     *
     * @param keys The raw keys to send (e.g., "C-c", "Escape").
     */
    suspend fun sendRawKeys(keys: String) = communicator.sendRawKeys(paneTarget, keys)

    /**
     * Returns true if this session currently exists in tmux.
     */
    suspend fun exists(): Boolean = existsChecker()
}
