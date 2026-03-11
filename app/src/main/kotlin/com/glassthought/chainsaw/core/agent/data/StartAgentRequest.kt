package com.glassthought.chainsaw.core.agent.data

import com.glassthought.chainsaw.core.data.PhaseType

/**
 * Input to [com.glassthought.chainsaw.core.agent.SpawnTmuxAgentSessionUseCase].
 *
 * Contains the information needed to spawn a new agent session.
 * Extensible for future fields (agent type override, model preference, etc.).
 *
 * @param phaseType The workflow phase this agent will operate in.
 * @param workingDir The directory the agent should operate in.
 */
data class StartAgentRequest(
    val phaseType: PhaseType,
    val workingDir: String,
)
