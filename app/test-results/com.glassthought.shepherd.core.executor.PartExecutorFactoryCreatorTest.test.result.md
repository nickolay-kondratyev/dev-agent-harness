---
spec: "com.glassthought.shepherd.core.executor.PartExecutorFactoryCreatorTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN buildFactory with a doer+reviewer part
  - WHEN creating a PartExecutor
    - [PASS] THEN returns a PartExecutorImpl
- GIVEN buildFactory with a doer-only part
  - WHEN creating a PartExecutor
    - [PASS] THEN returns a PartExecutorImpl
- GIVEN resolveIterationConfig
  - WHEN part has a reviewer with iteration config
    - [PASS] THEN returns the reviewer's iteration config
  - WHEN part is doer-only (no reviewer)
    - [PASS] THEN returns default iteration config with max=1
