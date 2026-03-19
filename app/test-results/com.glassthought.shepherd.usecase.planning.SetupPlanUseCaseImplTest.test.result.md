---
spec: "com.glassthought.shepherd.usecase.planning.SetupPlanUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a straightforward workflow
  - WHEN setup is called
    - [PASS] THEN does NOT route to DetailedPlanningUseCase
    - [PASS] THEN returns exactly one part
    - [PASS] THEN returns the parts from StraightforwardPlanUseCase
    - [PASS] THEN routes to StraightforwardPlanUseCase
- GIVEN a with-planning workflow
  - WHEN setup is called
    - [PASS] THEN does NOT route to StraightforwardPlanUseCase
    - [PASS] THEN returns the parts from DetailedPlanningUseCase
    - [PASS] THEN returns two parts
    - [PASS] THEN routes to DetailedPlanningUseCase
