# Implementation Plan: Switch GLM API to Anthropic-Compatible Endpoint

## Task Understanding
The GLM API is failing with HTTP 429 "Insufficient balance" because the code uses the OpenAI-compatible endpoint (`/api/paas/v4/chat/completions`). The user's GLM subscription works with the Anthropic-compatible endpoint (`/api/anthropic`).

**What "done" looks like:**
- Code uses Anthropic-compatible API endpoint
- Request format follows Anthropic style (includes `max_tokens`, model name changes)
- Response parsing handles Anthropic's `content[0].text` structure
- All tests pass
- Sandbox main can successfully call the API

## Codebase Recon
- **Constants.kt**: Contains endpoint URL and model name
- **GLMHighestTierApi.kt**: Builds request, handles response - needs format changes
- **GLMHighestTierApiTest.kt**: Tests for request/response - needs format updates
- **Initializer.kt**: Wires dependencies, no changes needed

## Quick Risk Check
- No blockers identified
- Clear requirements from user
- Anthropic API format is well-documented

---

## Plan

**Goal**: Switch GLM API implementation from OpenAI-compatible to Anthropic-compatible format.

**Steps**:
1. Update Constants.kt: Change endpoint URL to Anthropic-compatible URL
2. Update Constants.kt: Change model name to `claude-3-5-sonnet-20241022`
3. Update GLMHighestTierApi.kt: Add `max_tokens` parameter to request body
4. Update GLMHighestTierApi.kt: Change response parsing from `choices[0].message.content` to `content[0].text`
5. Update GLMHighestTierApi.kt: Update documentation comments
6. Update GLMHighestTierApiTest.kt: Change mock response format to Anthropic style
7. Update GLMHighestTierApiTest.kt: Add test for `max_tokens` in request body
8. Run tests to verify

**Testing**: All existing tests must pass with updated format.

**Files touched**:
- `app/src/main/kotlin/com/glassthought/Constants.kt`
- `app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt`
- `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt`

---

## Progress Tracking

- [x] Step 1: Update Constants.kt endpoint URL
- [x] Step 2: Update Constants.kt model name
- [x] Step 3: Add max_tokens to request body
- [x] Step 4: Update response parsing
- [x] Step 5: Update documentation
- [x] Step 6: Update test mock response format
- [x] Step 7: Add max_tokens test
- [x] Step 8: Run tests and verify

## Status: COMPLETE

All steps completed successfully. All tests pass.
