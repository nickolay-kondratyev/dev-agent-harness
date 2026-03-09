package com.glassthought.chainsaw.core.wingman

import com.asgard.core.annotation.AnchorPoint

/**
 * Resolves agent session IDs from a GUID handshake marker.
 *
 * The harness generates a GUID and sends it to the agent as the first TMUX message.
 * Implementations scan agent session artifacts (e.g., JSONL log files) to find which
 * session contains the GUID, thereby mapping a GUID to a concrete session ID.
 *
 * See ref.ap.7sZveqPcid5z1ntmLs27UqN6.E for TmuxSession (the caller context).
 */
@AnchorPoint("ap.gCgRdmWd9eTGXPbHJvyxI.E")
interface Wingman {

    /**
     * Resolves the session ID for the agent session that contains the given GUID.
     *
     * @param guid The unique handshake marker sent to the agent.
     * @return The session ID string (e.g., a UUID derived from the session artifact filename).
     * @throws IllegalStateException if no session or multiple sessions match the GUID.
     */
    suspend fun resolveSessionId(guid: String): String
}
