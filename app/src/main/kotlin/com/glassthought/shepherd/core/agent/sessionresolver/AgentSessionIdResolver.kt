package com.glassthought.shepherd.core.agent.sessionresolver

import com.asgard.core.annotation.AnchorPoint

/**
 * Resolves agent session IDs from a GUID handshake marker.
 *
 * The harness generates a GUID and sends it to the agent as the first TMUX message.
 * Implementations scan agent session artifacts (e.g., JSONL log files) to find which
 * session contains the GUID, thereby mapping a GUID to a concrete session ID.
 *
 * See ref.ap.7sZveqPcid5z1ntmLs27UqN6.E for TmuxSession (the caller context).
 * See ref.ap.gCgRdmWd9eTGXPbHJvyxI.E for the ClaudeCodeAgentSessionIdResolver implementation.
 */
@AnchorPoint("ap.D3ICqiFdFFgbFIPLMTYdoyss.E")
interface AgentSessionIdResolver {

    /**
     * Resolves the session ID for the agent session that contains the given GUID.
     *
     * @param guid The unique handshake marker sent to the agent.
     * @param model The model used to spawn this agent (e.g., "sonnet", "opus").
     *   Read from the sub-part config in `current_state.json` at spawn time and stored
     *   in [ResumableAgentSessionId] for session record persistence and V2 resume.
     * @return A [ResumableAgentSessionId] containing the agent type, session ID, and model
     *   (e.g., a UUID derived from the session artifact filename).
     * @throws IllegalStateException if no session or multiple sessions match the GUID.
     */
    suspend fun resolveSessionId(guid: HandshakeGuid, model: String): ResumableAgentSessionId
}
