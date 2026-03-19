---
spec: "com.glassthought.shepherd.core.state.CurrentStateDerivedStatusTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a non-existent part name
  - WHEN calling isPartCompleted
    - [PASS] THEN throws IllegalArgumentException
  - WHEN calling isPartFailed
    - [PASS] THEN throws IllegalArgumentException
- GIVEN a part where all sub-parts are COMPLETED
  - WHEN checking isPartCompleted
    - [PASS] THEN returns true
  - WHEN checking isPartFailed
    - [PASS] THEN returns false
- GIVEN a part where one sub-part is FAILED
  - WHEN checking isPartCompleted
    - [PASS] THEN returns false
  - WHEN checking isPartFailed
    - [PASS] THEN returns true
- GIVEN a part where one sub-part is IN_PROGRESS
  - WHEN checking isPartCompleted
    - [PASS] THEN returns false
  - WHEN checking isPartFailed
    - [PASS] THEN returns false
- GIVEN a state with all parts completed
  - WHEN finding resume point
    - [PASS] THEN returns null
- GIVEN a state with first part IN_PROGRESS
  - WHEN finding resume point
    - [PASS] THEN returns the first part (first non-completed)
- GIVEN a state with first part completed and second part NOT_STARTED
  - WHEN finding resume point
    - [PASS] THEN returns the second part
