package com.glassthought.ticketShepherd.core

object Constants {
  /** Model identifiers as sent to the provider's API (wire format). */
  object DIRECT_LLM_API_MODEL_NAME {
    /** GLM highest-tier model identifier for the Z.AI Anthropic-compatible API. */
    const val GLM_HIGHEST_TIER = "glm-5"

    /** GLM quick/cheap tier model identifier for Z.AI Anthropic-compatible API. */
    const val GLM_QUICK_CHEAP = "glm-4.7-flash"
  }

  /** Z.AI API configuration constants. */
  object Z_AI_API {
    /** Anthropic-compatible messages endpoint for Z.AI GLM subscription.
     * Base URL is `https://api.z.ai/api/anthropic`; `/v1/messages` is the messages path
     * required by the Anthropic Messages API spec. */
    const val CHAT_COMPLETIONS_ENDPOINT = "https://api.z.ai/api/anthropic/v1/messages"
    const val API_TOKEN_ENV_VAR = "Z_AI_GLM_API_TOKEN"
    /** Default max tokens for API responses. Can be overridden via environment variable. */
    const val MAX_TOKENS_ENV_VAR = "Z_AI_GLM_MAX_TOKENS"
    const val DEFAULT_MAX_TOKENS = 4096
    /** Anthropic-compatible API version header value required by the Z.AI messages endpoint. */
    const val ANTHROPIC_API_VERSION = "2023-06-01"
  }

  /** Returns the resolved max tokens value — from [Z_AI_API.MAX_TOKENS_ENV_VAR] env var if set, otherwise [Z_AI_API.DEFAULT_MAX_TOKENS]. */
  fun resolveMaxTokens(): Int =
    System.getenv(Z_AI_API.MAX_TOKENS_ENV_VAR)?.toIntOrNull() ?: Z_AI_API.DEFAULT_MAX_TOKENS
}
