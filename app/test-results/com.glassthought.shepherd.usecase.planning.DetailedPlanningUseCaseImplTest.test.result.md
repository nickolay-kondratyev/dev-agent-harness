---
spec: "com.glassthought.shepherd.usecase.planning.DetailedPlanningUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN planning executor always completes
  - AND plan conversion always throws PlanConversionException
    - AND maxConversionRetries is 2
      - [PASS] THEN failedToExecutePlanUseCase.handleFailure is called
      - [PASS] THEN the factory created 2 executors (one per retry attempt)
      - [PASS] THEN the failure result is FailedToConverge with conversion error message
- GIVEN planning executor completes
  - AND first plan conversion throws PlanConversionException
    - AND second plan conversion succeeds
      - [PASS] THEN execute returns execution parts from second attempt
      - [PASS] THEN failedToExecutePlanUseCase is NOT called on successful retry
      - [PASS] THEN the factory creates two executors (one per planning attempt)
- GIVEN planning executor completes successfully
  - AND plan conversion succeeds
    - [PASS] THEN execute returns the execution parts
    - [PASS] THEN failedToExecutePlanUseCase is NOT called
    - [PASS] THEN the returned list has correct size
- GIVEN planning executor returns AgentCrashed
  - WHEN execute is called
    - [PASS] THEN failedToExecutePlanUseCase.handleFailure is called with AgentCrashed
- GIVEN planning executor returns FailedToConverge
  - WHEN execute is called
    - [PASS] THEN failedToExecutePlanUseCase.handleFailure is called with FailedToConverge
- GIVEN planning executor returns FailedWorkflow
  - WHEN execute is called
    - [PASS] THEN failedToExecutePlanUseCase.handleFailure is called
    - [PASS] THEN planFlowConverter.convertAndAppend is NOT called
    - [PASS] THEN the PartResult passed to handleFailure is FailedWorkflow with correct reason
