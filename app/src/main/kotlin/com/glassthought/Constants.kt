package com.glassthought

object Constants {
  object LLM_MODEL_NAME {
    val GLM_HIGHEST_TIER = "GLM-5"
  }

  fun getConfigurationObject(): Config {
    return Config(
      zAiGlmConfig = ModelNamesConfig(
        highestTier = LLM_MODEL_NAME.GLM_HIGHEST_TIER
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



