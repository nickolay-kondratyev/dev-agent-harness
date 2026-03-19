---
spec: "com.glassthought.shepherd.usecase.planning.StraightforwardPlanUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a straightforward workflow with a single part
  - WHEN execute is called
    - [PASS] THEN returns a list with one part
    - [PASS] THEN the returned part matches the workflow definition
- GIVEN a straightforward workflow with predefined parts
  - WHEN execute is called
    - [PASS] THEN returns the correct number of parts
    - [PASS] THEN returns the parts from the workflow definition
