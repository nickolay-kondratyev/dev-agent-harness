---
spec: "com.glassthought.shepherd.core.TicketShepherdTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a part with 1 sub-part (doer only)
  - WHEN run() is called
    - [PASS] THEN factory receives the part with exactly 1 sub-part
- GIVEN a part with 2 sub-parts (doer + reviewer)
  - WHEN run() is called
    - [PASS] THEN factory receives the part with exactly 2 sub-parts
    - [PASS] THEN factory receives the reviewer sub-part as second element
- GIVEN a plan where a part returns AgentCrashed
  - WHEN run() is called
    - [PASS] THEN delegates to FailedToExecutePlanUseCase
- GIVEN a plan where a part returns FailedToConverge
  - WHEN run() is called
    - [PASS] THEN delegates to FailedToExecutePlanUseCase
- GIVEN a plan where the first part returns FailedWorkflow
  - WHEN run() is called
    - [PASS] THEN delegates to FailedToExecutePlanUseCase with the failure result
    - [PASS] THEN does NOT call finalCommitUseCase
    - [PASS] THEN does NOT create an executor for the second part
    - [PASS] THEN does NOT mark the ticket as done
- GIVEN a plan with no parts (empty plan)
  - WHEN run() is called
    - [PASS] THEN exits with code 0
    - [PASS] THEN still performs final commit
- GIVEN a plan with two parts that both complete successfully
  - WHEN run() is called
    - [PASS] THEN activeExecutor is null after run completes
    - [PASS] THEN appends parts to currentState
    - [PASS] THEN creates executors for each part via the factory
    - [PASS] THEN exits with code 0
    - [PASS] THEN installs the interrupt handler
    - [PASS] THEN kills all sessions as defensive cleanup
    - [PASS] THEN marks the ticket as done
    - [PASS] THEN performs the final commit
    - [PASS] THEN prints success message in green with ticket ID
- GIVEN activeExecutor tracking during execution
  - WHEN a part is being executed
    - [PASS] THEN activeExecutor is non-null during execution and null after
- GIVEN originatingBranch and tryNumber
  - [PASS] THEN they are accessible on the TicketShepherd instance
- GIVEN success path ordering
  - WHEN all parts complete
    - [PASS] THEN steps execute in order: finalCommit -> markDone -> killSessions -> printGreen -> exit
