---
spec: "com.glassthought.shepherd.core.sessionresolver.impl.ClaudeCodeAgentSessionIdResolverTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a ClaudeCodeAgentSessionIdResolver with a fake GuidScanner
  - AND the scanner always returns empty and timeout is very short
    - WHEN resolveSessionId is called
      - [PASS] THEN exception message contains the GUID
      - [PASS] THEN throws IllegalStateException wrapping the timeout
  - AND the scanner returns empty on first call then a match on the second call
    - WHEN resolveSessionId is called
      - [PASS] THEN polls more than once before finding the match
      - [PASS] THEN returns the session ID from the matched path
  - AND the scanner returns empty on the first 3 calls then a match
    - WHEN resolveSessionId is called
      - [PASS] THEN returns the session ID after 4 total poll attempts
- GIVEN a ClaudeCodeAgentSessionIdResolver with a temp projects directory
  - AND JSONL files are in nested subdirectories
    - WHEN resolveSessionId is called
      - [PASS] THEN finds the GUID in nested files and returns session ID
  - AND a single JSONL file containing the target GUID
    - WHEN resolveSessionId is called
      - [PASS] THEN returns the session ID extracted from the filename
  - AND multiple JSONL files contain the target GUID
    - WHEN resolveSessionId is called
      - [PASS] THEN exception message mentions ambiguous
      - [PASS] THEN throws IllegalStateException
  - AND no JSONL files contain the target GUID
    - WHEN resolveSessionId is called with a short timeout
      - [PASS] THEN exception message contains the GUID
      - [PASS] THEN throws IllegalStateException
  - AND only non-JSONL files contain the GUID
    - WHEN resolveSessionId is called with a short timeout
      - [PASS] THEN throws IllegalStateException because non-JSONL files are ignored
