package com.glassthought.shepherd.core.agent.tmux.data

/**
 * Represents a tmux session that has been created.
 *
 * See [com.glassthought.shepherd.core.agent.tmux.TmuxSession]/ref.ap.7sZveqPcid5z1ntmLs27UqN6.E for the full session representation.
 *
 * @param sessionName The name used to identify the session in tmux.
 */
data class TmuxSessionName(val sessionName: String)