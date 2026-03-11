package com.glassthought.chainsaw.core.agent.data

/**
 * Wraps the shell command string used to start an agent inside a tmux session.
 *
 * Provides type safety so callers cannot accidentally pass arbitrary strings
 * where a tmux start command is expected.
 */
@JvmInline
value class TmuxStartCommand(val command: String)
