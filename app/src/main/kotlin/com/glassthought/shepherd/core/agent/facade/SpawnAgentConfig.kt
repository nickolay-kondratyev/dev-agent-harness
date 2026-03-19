package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.data.AgentType
import java.nio.file.Path

/**
 * Configuration for spawning a new agent session via [AgentFacade.spawnAgent].
 *
 * Carries all parameters needed by the facade to bootstrap a TMUX session, perform the
 * handshake, and register the session in [SessionsState] (ref.ap.7V6upjt21tOoCFXA7nqNh.E).
 *
 * @property partName Name of the part this agent is executing (e.g., "part_1_backend").
 * @property subPartName Name of the sub-part within the part (e.g., "doer", "reviewer").
 * @property subPartIndex Zero-based index of the sub-part within the part.
 * @property agentType Type of agent to spawn (e.g., [AgentType.CLAUDE_CODE]).
 * @property model Model identifier for the agent (e.g., "claude-sonnet-4-20250514").
 * @property role Role name used for context/instruction assembly (e.g., "DOER", "REVIEWER").
 * @property systemPromptPath Path to the system prompt file for the agent.
 * @property bootstrapMessage Initial message sent to the agent after TMUX session creation
 *   to initiate the handshake protocol (ref.ap.hZdTRho3gQwgIXxoUtTqy.E).
 */
@AnchorPoint("ap.nDDZyl11vax5mqhyAiiDr.E")
data class SpawnAgentConfig(
    val partName: String,
    val subPartName: String,
    val subPartIndex: Int,
    val agentType: AgentType,
    val model: String,
    val role: String,
    val systemPromptPath: Path,
    val bootstrapMessage: String,
)
