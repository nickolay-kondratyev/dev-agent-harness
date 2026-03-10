package com.glassthought.chainsaw.core.server

/**
 * Request data classes for agent-to-harness HTTP endpoints.
 *
 * Each endpoint has its own type for type safety, even when some share the same shape today.
 * Jackson + Kotlin module handles deserialization from JSON.
 *
 * JSON payloads match the contract defined in harness-cli-for-agent.sh
 * (ref.ap.8PB8nMd93D3jipEWhME5n.E).
 */

/** Common interface for all agent-to-harness request payloads. */
interface AgentRequest {
    val branch: String
}

/** POST /agent/done — agent signals task completion. */
data class AgentDoneRequest(override val branch: String) : AgentRequest

/** POST /agent/question — agent asks the harness a question. */
data class AgentQuestionRequest(override val branch: String, val question: String) : AgentRequest

/** POST /agent/failed — agent signals unrecoverable failure. */
data class AgentFailedRequest(override val branch: String, val reason: String) : AgentRequest

/** POST /agent/status — agent replies to a health ping. */
data class AgentStatusRequest(override val branch: String) : AgentRequest
