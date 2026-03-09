---
closed_iso: 2026-03-09T18:00:06Z
id: nid_1g95kqaiyfcwlqnop8be6deud_E
title: "Add GLM5 API caller"
status: closed
deps: []
links: []
created_iso: 2026-03-07T23:35:30Z
status_updated_iso: 2026-03-09T18:00:06Z
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

---

## Resolution

**Completed** on branch `CC_nid_1g95kqaiyfcwlqnop8be6deud_E__add-glm5-api-caller_opus-v4.6`.

### What was implemented
- `DirectLLM` interface (`ChatRequest` string in, `ChatResponse` string out) - API-agnostic
- `GLMHighestTierApi` implementation using OkHttp + org.json for Z.AI chat completions API
- `Initializer` refactored to be root of all dependency wiring (including OutFactory)
- `Constants` updated: `DIRECT_LLM_API_MODEL_NAME.GLM_HIGHEST_TIER = "glm-5"`, `Z_AI_API` endpoint/env-var constants
- `App.kt` delegates to `Initializer`
- 12 unit tests (MockWebServer), 1 integration test (gated)
- Dependencies added: OkHttp 4.12.0, org.json 20240303, MockWebServer 4.12.0 (test)

