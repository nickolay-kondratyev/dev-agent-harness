---
spec: "com.glassthought.shepherd.core.initializer.data.EnvironmentTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN Environment.production()
  - WHEN isTest is checked
    - [PASS] THEN isTest is false
- GIVEN Environment.test()
  - WHEN isTest is checked
    - [PASS] THEN isTest is true
- GIVEN TestEnvironment
  - WHEN isTest is checked
    - [PASS] THEN isTest is true
