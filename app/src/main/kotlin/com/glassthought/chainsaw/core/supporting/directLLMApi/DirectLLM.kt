package com.glassthought.chainsaw.core.supporting.directLLMApi

/**
 * Request to a direct LLM API call.
 * V1: just a prompt string.
 */
data class ChatRequest(val prompt: String)

/**
 * Response from a direct LLM API call.
 * V1: just the response text.
 */
data class ChatResponse(val text: String)

/**
 * API-agnostic interface for calling LLMs directly (not via agents).
 *
 * Implementations handle the specific API protocol (authentication, request
 * format, response parsing) for a given LLM provider.
 */
interface DirectLLM {
    suspend fun call(request: ChatRequest): ChatResponse
}
