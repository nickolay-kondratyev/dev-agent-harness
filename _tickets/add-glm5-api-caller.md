---
id: nid_1g95kqaiyfcwlqnop8be6deud_E
title: "Add GLM5 API caller"
status: in_progress
deps: []
links: []
created_iso: 2026-03-07T23:35:30Z
status_updated_iso: 2026-03-09T17:05:48Z
type: task
priority: 3
assignee: nickolaykondratyev
---

Let's make an interface that is API agnostic for calling LLMs directly I am thinking of a namespace that would clarify that these are direct API calls like 

./app/src/main/kotlin/com/glassthought/directLLMApi/DirectLLM.kt - interface that is agnostic of model implementation it will expose the API to call the LLM and get responses back. This will be direct LLM call not an agent so we will want to call the API directly.

./app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt - GLM5 implementation of the DirectLLM interface. 

This implementation will use a conifiguration. object that will be coming from the constant but we will want to have this be wired in the initializer  (./app/src/main/kotlin/com/glassthought/initializer/Initializer.kt)

Config:
```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/Constants.kt] Lines=[8-14]
  fun getConfigurationObject(): Config {
    return Config(
      zAiGlmConfig = ModelNamesConfig(
        highestTier = LLM_MODEL_NAME.GLM_HIGHEST_TIER
      )
    )
  }
```

The token for the API call will be coming from environment variable: `$Z_AI_GLM_API_TOKEN`

