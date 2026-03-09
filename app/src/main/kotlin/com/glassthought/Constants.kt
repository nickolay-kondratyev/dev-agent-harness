package com.glassthought

object Constants {
  /** Model identifiers as sent to the provider's API (wire format). */
  object DIRECT_LLM_API_MODEL_NAME {
    /** GLM highest-tier model identifier for the Z.AI Anthropic-compatible API. */
    const val GLM_HIGHEST_TIER = "glm-5"
  }

  /** Z.AI API configuration constants. */
  object Z_AI_API {
    /** Anthropic-compatible endpoint for Z.AI GLM subscription. */
    const val CHAT_COMPLETIONS_ENDPOINT = "https://api.z.ai/api/anthropic"
    const val API_TOKEN_ENV_VAR = "Z_AI_GLM_API_TOKEN"
    /** Default max tokens for API responses. Can be overridden via environment variable. */
    const val MAX_TOKENS_ENV_VAR = "Z_AI_GLM_MAX_TOKENS"
    const val DEFAULT_MAX_TOKENS = 4096
  }

  fun getConfigurationObject(): Config {
    val maxTokens = System.getenv(Z_AI_API.MAX_TOKENS_ENV_VAR)?.toIntOrNull()
      ?: Z_AI_API.DEFAULT_MAX_TOKENS

    return Config(
      zAiGlmConfig = GLMDirectLLMConfig(
        modelName = DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER,
        maxTokens = maxTokens,
      )
    )
  }
}

data class GLMDirectLLMConfig(
  val modelName: String,
  val maxTokens: Int,
)

data class Config(
  /** Configuration for https://chat.z.ai models
   *
   *  GLM could change in the future as the frontier model of Z.AI, BUT
   *  for now its much easier to remember that we are talking about Z.AI with
   *  GLM naming*/
  val zAiGlmConfig: GLMDirectLLMConfig
)
