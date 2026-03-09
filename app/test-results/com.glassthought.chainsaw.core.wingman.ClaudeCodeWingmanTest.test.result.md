---
spec: "com.glassthought.chainsaw.core.wingman.ClaudeCodeWingmanTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a ClaudeCodeWingman with a temp projects directory
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
    - WHEN resolveSessionId is called
      - [PASS] THEN exception message contains the GUID
      - [PASS] THEN throws IllegalStateException
