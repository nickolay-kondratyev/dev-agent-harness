---
spec: "com.glassthought.directLLMApi.glm.GLMHighestTierApiTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN GLM API with MockWebServer
  - WHEN API returns empty content array
    - [PASS] THEN throws IllegalStateException mentioning empty content
  - WHEN API returns malformed JSON
    - [PASS] THEN throws IllegalStateException indicating parse failure
  - WHEN API returns non-2xx status
    - [PASS] THEN throws IllegalStateException with status code information
  - WHEN call is made with a simple prompt
    - [PASS] THEN request body contains the model name
    - [PASS] THEN request body has a max_tokens field
    - [PASS] THEN request body has exactly one message
    - [PASS] THEN request body max_tokens value matches configured value
    - [PASS] THEN request body message content matches prompt
    - [PASS] THEN request body message role is user
    - [PASS] THEN request has correct Content-Type header
    - [PASS] THEN request has correct x-api-key header
    - [PASS] THEN request method is POST
    - [PASS] THEN response text matches the mock response content
  - WHEN prompt contains special characters needing JSON escaping
    - [PASS] THEN request body is valid JSON with correctly escaped content
    - [PASS] THEN response is returned successfully
- GIVEN a budget-high GLM API instance
  - [PASS] THEN it implements DirectBudgetHighLLM
