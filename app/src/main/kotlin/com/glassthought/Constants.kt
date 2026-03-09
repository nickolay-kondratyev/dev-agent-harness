package com.glassthought

object Constants {
  /** Model identifiers as sent to the provider's API (wire format). */
  object DIRECT_LLM_API_MODEL_NAME {
    /** GLM highest-tier model identifier for the Z.AI Anthropic-compatible API. */
    const val GLM_HIGHEST_TIER = "claude-3-5-sonnet-20241022"
  }

  /** Z.AI API configuration constants. */
  object Z_AI_API {
    /** Anthropic-compatible endpoint for Z.AI GLM subscription. */
    const val CHAT_COMPLETIONS_ENDPOINT = "https://api.z.ai/api/anthropic"
    const val API_TOKEN_ENV_VAR = "Z_AI_GLM_API_TOKEN"
  }

  fun getConfigurationObject(): Config {
    return Config(
      zAiGlmConfig = ModelNamesConfig(
        highestTier = DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER
      )
    )
  }
}

data class ModelNamesConfig(
  val highestTier: String
)

data class Config(
  /** Configuration for https://chat.z.ai models
   *
   *  GLM could change in the future as the frontier model of Z.AI, BUT
   *  for now its much easier to remember that we are talking about Z.AI with
   *  GLM naming*/
  val zAiGlmConfig: ModelNamesConfig
)
