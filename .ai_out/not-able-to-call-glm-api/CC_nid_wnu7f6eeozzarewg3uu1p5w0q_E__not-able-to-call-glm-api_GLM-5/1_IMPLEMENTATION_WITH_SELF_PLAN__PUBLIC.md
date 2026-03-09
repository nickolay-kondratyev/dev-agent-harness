# Implementation Complete: Switch GLM API to Anthropic-Compatible Endpoint

## Summary

Successfully switched the GLM API implementation from OpenAI-compatible format to Anthropic-compatible format to resolve the HTTP 429 "Insufficient balance" error.

## Root Cause
The code was using the OpenAI-compatible endpoint (`/api/paas/v4/chat/completions`) which requires separate API credits. The user's GLM subscription works with the Anthropic-compatible endpoint (`/api/anthropic`).

## Changes Made

### 1. Constants.kt
**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/Constants.kt`

- Changed `CHAT_COMPLETIONS_ENDPOINT` from `https://api.z.ai/api/paas/v4/chat/completions` to `https://api.z.ai/api/anthropic`
- Changed model name from `glm-5` to `claude-3-5-sonnet-20241022` (Anthropic-compatible model identifier)

### 2. GLMHighestTierApi.kt
**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt`

**Request format changes:**
- Changed Authorization header from `Bearer $apiToken` to `x-api-key: $apiToken`
- Added `anthropic-version: 2023-06-01` header (required by Anthropic API)
- Added `max_tokens: 4096` parameter to request body (required by Anthropic API)

**Response parsing changes:**
- Changed from OpenAI's `choices[0].message.content` to Anthropic's `content[0].text`
- Added validation that the content block type is "text"

### 3. GLMHighestTierApiTest.kt
**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt`

- Updated model name to `claude-3-5-sonnet-20241022`
- Changed mock response format from OpenAI's `{"choices": [{"message": {"content": "..."}}]}` to Anthropic's `{"content": [{"type": "text", "text": "..."}]}`
- Updated Authorization header test to check for `x-api-key` instead of `Bearer`
- Added new test for `max_tokens` parameter in request body
- Updated empty response test from "empty choices" to "empty content"

## Test Results

All tests pass:
```
BUILD SUCCESSFUL in 7s
28 actionable tasks: 7 executed, 21 up-to-date
```

## API Format Comparison

### Before (OpenAI-compatible)
```json
// Request
{
  "model": "glm-5",
  "messages": [{"role": "user", "content": "..."}]
}

// Response
{
  "choices": [{
    "message": {"role": "assistant", "content": "..."}
  }]
}
```

### After (Anthropic-compatible)
```json
// Request
{
  "model": "claude-3-5-sonnet-20241022",
  "max_tokens": 4096,
  "messages": [{"role": "user", "content": "..."}]
}

// Response
{
  "content": [{
    "type": "text",
    "text": "..."
  }]
}
```

## Environment Variables
No changes required - still uses `Z_AI_GLM_API_TOKEN` environment variable.

## Notes
- The same env var `Z_AI_GLM_API_TOKEN` is used, just sent via `x-api-key` header instead of `Bearer` Authorization
- The `max_tokens` value is set to 4096, which is a reasonable default for most use cases
- The implementation now validates that response content blocks are of type "text" before extracting the text
