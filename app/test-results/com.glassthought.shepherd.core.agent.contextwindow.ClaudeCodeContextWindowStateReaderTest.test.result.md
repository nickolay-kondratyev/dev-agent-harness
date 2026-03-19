---
spec: "com.glassthought.shepherd.core.agent.contextwindow.ClaudeCodeContextWindowStateReaderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a JSON file with invalid timestamp format
  - WHEN read is called
    - [PASS] THEN throws ContextWindowStateUnavailableException
- GIVEN a JSON file with remaining_percentage outside valid range
  - WHEN read is called
    - [PASS] THEN throws ContextWindowStateUnavailableException
- GIVEN a malformed JSON file with missing fields
  - WHEN read is called
    - [PASS] THEN throws ContextWindowStateUnavailableException
- GIVEN a timestamp exactly at the stale boundary
  - WHEN read is called
    - [PASS] THEN returns ContextWindowState with remainingPercentage (not stale)
- GIVEN a valid JSON file with fresh timestamp
  - WHEN read is called
    - [PASS] THEN returns ContextWindowState with remainingPercentage
- GIVEN a valid JSON file with stale timestamp
  - WHEN read is called
    - [PASS] THEN returns ContextWindowState with remainingPercentage = null
- GIVEN an unparseable JSON file
  - WHEN read is called
    - [PASS] THEN throws ContextWindowStateUnavailableException
- GIVEN the JSON file does not exist
  - WHEN read is called
    - [PASS] THEN throws ContextWindowStateUnavailableException
