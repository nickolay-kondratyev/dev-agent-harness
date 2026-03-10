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

/** POST /agent/done — agent signals task completion. */
data class AgentDoneRequest(val branch: String)

/** POST /agent/question — agent asks the harness a question. */
data class AgentQuestionRequest(val branch: String, val question: String)

/** POST /agent/failed — agent signals unrecoverable failure. */
data class AgentFailedRequest(val branch: String, val reason: String)

/** POST /agent/status — agent replies to a health ping. */
data class AgentStatusRequest(val branch: String)
