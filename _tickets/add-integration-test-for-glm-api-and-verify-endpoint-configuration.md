---
closed_iso: 2026-03-09T20:50:16Z
id: nid_7v3qgmsyt7rzzsrar2w8vll4n_E
title: "Add integration test for GLM API and verify endpoint configuration"
status: closed
deps: []
links: []
created_iso: 2026-03-09T20:39:13Z
status_updated_iso: 2026-03-09T20:50:16Z
type: task
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

## Problem
The GLM API call is returning a 404 NOT_FOUND error when using the Anthropic-compatible endpoint.

### Error observed
```
response_snippet=[{"code":500,"msg":"404 NOT_FOUND","success":false}]
```

### Current configuration
- Endpoint: `https://api.z.ai/api/anthropic`
- Model: `glm-5`
- Headers: `x-api-key`, `anthropic-version: 2023-06-01`
- Request format: Anthropic Messages API

### Tasks
1. **Add integration test** that actually calls the GLM API and verifies successful response
2. **Verify endpoint configuration** - investigate if the endpoint URL or request format needs adjustment
3. **Keep response parsing simple** - align with the actual API response format

### Files to update
- `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiIntegTest.kt`
- `app/src/main/kotlin/com/glassthought/Constants.kt` (if endpoint needs adjustment)
- `app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt` (if request/response format needs adjustment)

### Reference
Working Claude Code configuration:
```bash
export ANTHROPIC_BASE_URL="https://api.z.ai/api/anthropic"
export ANTHROPIC_AUTH_TOKEN="${ZAI_API_KEY}"
```

### Acceptance criteria
- Integration test passes when run with `-PrunIntegTests=true`
- API call returns successful response with text content
- Response parsing correctly extracts content from API response


## Notes

**2026-03-09T20:50:10Z**

## Resolution

Root cause: `Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT` was set to `https://api.z.ai/api/anthropic` (the base URL), but the Anthropic Messages API requires the full path `/v1/messages` appended.

Fix: Updated endpoint to `https://api.z.ai/api/anthropic/v1/messages`.

Integration test `GLMHighestTierApiIntegTest` already existed and passes with the corrected endpoint when run with `-PrunIntegTests=true` and `Z_AI_GLM_API_TOKEN` set.
