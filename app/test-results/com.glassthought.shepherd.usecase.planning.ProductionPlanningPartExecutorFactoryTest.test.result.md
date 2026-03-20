---
spec: "com.glassthought.shepherd.usecase.planning.ProductionPlanningPartExecutorFactoryTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a doer+reviewer planning part
  - WHEN create() is called
    - [PASS] THEN returns a PartExecutorImpl
- GIVEN a doer-only planning part
  - WHEN create() is called with empty priorConversionErrors
    - [PASS] THEN returns a PartExecutorImpl
  - WHEN create() is called with non-empty priorConversionErrors
    - [PASS] THEN still returns a PartExecutorImpl
