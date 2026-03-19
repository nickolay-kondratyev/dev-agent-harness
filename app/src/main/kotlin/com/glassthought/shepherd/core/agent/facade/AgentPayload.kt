package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.annotation.AnchorPoint
import java.nio.file.Path

/**
 * Payload delivered to an agent via [AgentFacade.sendPayloadAndAwaitSignal].
 *
 * Carries the path to the instruction file that the agent should read and execute.
 * The instruction file is assembled by `ContextForAgentProvider`
 * (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) before each send.
 *
 * @property instructionFilePath Absolute path to the instruction file for the agent.
 *   The facade wraps this path in the ACK protocol envelope and delivers it via TMUX send-keys.
 */
@AnchorPoint("ap.dPr77qbXaTmUgH0R3OBq0.E")
data class AgentPayload(
    val instructionFilePath: Path,
)
