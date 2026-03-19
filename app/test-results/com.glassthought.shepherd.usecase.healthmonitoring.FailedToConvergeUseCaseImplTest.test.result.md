---
spec: "com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN iteration budget exhausted
  - GIVEN custom iteration increment
    - WHEN prompt is displayed
      - [PASS] THEN prompt uses custom increment value
  - WHEN prompt is displayed
    - [PASS] THEN prompt contains correct iteration counts and increment
  - WHEN readLine returns null
    - [PASS] THEN returns false
  - WHEN user enters "N"
    - [PASS] THEN returns false
  - WHEN user enters "Y"
    - [PASS] THEN returns true
  - WHEN user enters "y"
    - [PASS] THEN returns true
  - WHEN user enters empty string
    - [PASS] THEN returns false
