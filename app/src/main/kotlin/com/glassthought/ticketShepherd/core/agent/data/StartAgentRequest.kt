package com.glassthought.ticketShepherd.core.agent.data

import com.glassthought.ticketShepherd.core.data.PhaseType

/**
 * Input to [com.glassthought.ticketShepherd.core.useCase.SpawnTmuxAgentSessionUseCase].
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
