package com.glassthought.tmux

import com.glassthought.tmux.data.TmuxSessionName

/**
 * Represents a live tmux session. Acts as the primary handle for interacting with a session.
 *
 * Encapsulates the session name, key-sending capability, and existence checking.
 * The [existsChecker] lambda avoids a circular dependency with [TmuxSessionManager].
 *
 * Instances are created by [TmuxSessionManager.createSession].
 */
class TmuxSession(
    val name: TmuxSessionName,
    private val communicator: TmuxCommunicator,
    private val existsChecker: suspend () -> Boolean,
) {
    /**
     * Sends text followed by the Enter key to this tmux session.
     *
     * @param text The text to type, followed by Enter.
     */
    suspend fun sendKeys(text: String) = communicator.sendKeys(name, text)

    /**
     * Sends raw keys to this tmux session without appending Enter.
     *
     * @param keys The raw keys to send (e.g., "C-c", "Escape").
     */
    suspend fun sendRawKeys(keys: String) = communicator.sendRawKeys(name, keys)

    /**
     * Returns true if this session currently exists in tmux.
     */
    suspend fun exists(): Boolean = existsChecker()
}
