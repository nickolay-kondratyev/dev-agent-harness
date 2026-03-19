---
spec: "com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN TicketFailureLearningUseCase throws
  - WHEN handleFailure is called
    - [PASS] THEN still exits with code 1 (learning failure is non-fatal)
    - [PASS] THEN still kills all sessions even when learning fails
- GIVEN a Completed result
  - WHEN handleFailure is called
    - [PASS] THEN throws IllegalArgumentException
- GIVEN a FailedToConverge result
  - WHEN handleFailure is called
    - [PASS] THEN exits with code 1
    - [PASS] THEN kills all sessions
    - [PASS] THEN prints convergence failure
- GIVEN a FailedWorkflow result
  - WHEN handleFailure is called
    - [PASS] THEN exits with code 1
    - [PASS] THEN kills all sessions
    - [PASS] THEN prints failure reason
    - [PASS] THEN records failure learning
- GIVEN an AgentCrashed result
  - WHEN handleFailure is called
    - [PASS] THEN exits with code 1
    - [PASS] THEN kills all sessions
    - [PASS] THEN prints crash details
- GIVEN an order tracker wired into all fakes
  - WHEN handleFailure is called
    - [PASS] THEN steps execute in order: print -> kill -> learning -> exit
