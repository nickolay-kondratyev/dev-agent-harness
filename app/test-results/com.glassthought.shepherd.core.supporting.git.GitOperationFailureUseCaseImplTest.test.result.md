---
spec: "com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN 'unable to lock' error variant AND lock file exists
  - AND retry succeeds
    - [PASS] THEN returns normally (triggers fast-path)
- GIVEN fail-fast escalation
  - [PASS] THEN FailedWorkflow reason contains git status output
  - [PASS] THEN FailedWorkflow reason contains iteration number
  - [PASS] THEN FailedWorkflow reason contains sub-part name
  - [PASS] THEN FailedWorkflow reason contains the git command
  - [PASS] THEN FailedWorkflow reason contains the part name
  - [PASS] THEN FailedWorkflow reason contains the stderr
- GIVEN index.lock error AND lock file exists on disk
  - AND retry fails
    - [PASS] THEN deletes the index.lock file before retrying
    - [PASS] THEN escalates to FailedToExecutePlanUseCase
    - [PASS] THEN escalation contains FailedWorkflow with git command info
  - AND retry succeeds
    - [PASS] THEN deletes the index.lock file
    - [PASS] THEN does NOT call FailedToExecutePlanUseCase
    - [PASS] THEN returns normally (no escalation)
- GIVEN index.lock error but lock file does NOT exist on disk
  - [PASS] THEN does NOT attempt to delete the lock file
  - [PASS] THEN escalates to FailedToExecutePlanUseCase immediately
- GIVEN non-lock-related error
  - [PASS] THEN does NOT attempt to delete the lock file
  - [PASS] THEN escalates to FailedToExecutePlanUseCase immediately
