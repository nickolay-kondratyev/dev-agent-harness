package com.glassthought.shepherd.core.supporting.directLLMApi

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

/** Fast, low-cost tasks (title compression, branch name slugification). V1: GLM-4.7-Flash. */
interface DirectQuickCheapLLM : DirectLLM

/** Mid-tier tasks. Reserved — no V1 callers yet. */
interface DirectMediumLLM : DirectLLM

/** Expensive tasks (FailedToConvergeUseCase state summarization). V1: GLM-5. */
interface DirectBudgetHighLLM : DirectLLM
