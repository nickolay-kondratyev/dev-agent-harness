---
spec: "com.glassthought.shepherd.core.agent.adapter.CallbackScriptsDirTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN CallbackScriptsDir.unvalidated
  - WHEN called with any path string
    - [PASS] THEN returns a CallbackScriptsDir without filesystem validation
- GIVEN CallbackScriptsDir.validated
  - AND a file path is provided instead of a directory
    - WHEN validated is called
      - [PASS] THEN throws IllegalStateException about not being a directory
  - AND a valid directory with executable callback_shepherd.signal.sh
    - WHEN validated is called
      - [PASS] THEN returns a CallbackScriptsDir with the correct path
  - AND the directory does not exist
    - WHEN validated is called
      - [PASS] THEN exception message contains the path
      - [PASS] THEN throws IllegalStateException
  - AND the directory exists but callback_shepherd.signal.sh is missing
    - WHEN validated is called
      - [PASS] THEN throws IllegalStateException
  - AND the directory exists with callback_shepherd.signal.sh but script is not executable
    - WHEN validated is called
      - [PASS] THEN throws IllegalStateException about executable permission
- GIVEN two CallbackScriptsDir instances with different paths
  - [PASS] THEN they are not equal
- GIVEN two CallbackScriptsDir instances with the same path
  - [PASS] THEN they are equal
  - [PASS] THEN they have the same hashCode
