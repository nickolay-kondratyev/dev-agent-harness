---
spec: "com.glassthought.shepherd.core.executor.PartCompletionGuardTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a non-existent pending directory
  - WHEN the guard validates
    - [PASS] THEN the result is Passed
- GIVEN a pending directory with a critical feedback file
  - WHEN the guard validates
    - [PASS] THEN the failure message mentions the file name
    - [PASS] THEN the result is Failed
- GIVEN a pending directory with an important feedback file
  - WHEN the guard validates
    - [PASS] THEN the failure message mentions the file name
    - [PASS] THEN the result is Failed
- GIVEN a pending directory with both optional and critical files
  - WHEN the guard validates
    - [PASS] THEN optional files are NOT moved (guard fails before moving)
    - [PASS] THEN the result is Failed (critical blocks completion)
- GIVEN a pending directory with both optional and important files
  - WHEN the guard validates
    - [PASS] THEN the result is Failed (important blocks completion)
- GIVEN a pending directory with multiple critical and important files
  - WHEN the guard validates
    - [PASS] THEN the failure message lists all blocking files
- GIVEN a pending directory with multiple optional files and no blocking files
  - WHEN the guard validates
    - [PASS] THEN all optional files are moved to addressed
- GIVEN a pending directory with only optional feedback files
  - WHEN the guard validates
    - [PASS] THEN the moved file retains its content
    - [PASS] THEN the optional file is moved to addressed directory
    - [PASS] THEN the result is Passed
- GIVEN an empty pending directory
  - WHEN the guard validates
    - [PASS] THEN the result is Passed
