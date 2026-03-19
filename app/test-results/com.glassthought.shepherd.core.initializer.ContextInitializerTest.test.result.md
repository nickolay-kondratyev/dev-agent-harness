---
spec: "com.glassthought.shepherd.core.initializer.ContextInitializerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN ContextInitializer with empty ZAI API key file
  - WHEN initialize is called
    - [PASS] THEN throws IllegalStateException mentioning empty key file
- GIVEN ContextInitializer with missing MY_ENV
  - WHEN initialize is called
    - [PASS] THEN throws IllegalStateException mentioning MY_ENV
- GIVEN ContextInitializer with missing ZAI API key file
  - WHEN initialize is called
    - [PASS] THEN throws IllegalStateException mentioning ZAI API key
- GIVEN ContextInitializer with valid configuration
  - WHEN initialize is called
    - [PASS] THEN ShepherdContext has a nonInteractiveAgentRunner
    - [PASS] THEN ShepherdContext has infra with outFactory
    - [PASS] THEN returns a ShepherdContext
- GIVEN ContextInitializer with whitespace-only ZAI API key file
  - WHEN initialize is called
    - [PASS] THEN throws IllegalStateException mentioning empty key file
