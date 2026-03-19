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
    - [PASS] THEN prints convergence failure in red
- GIVEN a FailedWorkflow result
  - WHEN handleFailure is called
    - [PASS] THEN exits with code 1
    - [PASS] THEN kills all sessions
    - [PASS] THEN output contains ANSI red escape code
    - [PASS] THEN output contains ANSI reset escape code
    - [PASS] THEN prints failure reason in red
    - [PASS] THEN records failure learning
- GIVEN an AgentCrashed result
  - WHEN handleFailure is called
    - [PASS] THEN exits with code 1
    - [PASS] THEN kills all sessions
    - [PASS] THEN prints crash details in red
