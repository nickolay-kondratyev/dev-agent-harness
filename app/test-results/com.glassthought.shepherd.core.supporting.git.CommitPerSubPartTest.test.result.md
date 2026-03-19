---
spec: "com.glassthought.shepherd.core.supporting.git.CommitPerSubPartTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN PI agent type
  - WHEN onSubPartDone is called
    - [PASS] THEN uses PI agent short code in author
- GIVEN changes exist after sub-part done
  - WHEN onSubPartDone is called
    - [PASS] THEN checks for staged changes via git diff --cached --quiet
    - [PASS] THEN creates commit with correct author and message
    - [PASS] THEN executes exactly 3 git commands
    - [PASS] THEN executes git add -A first
- GIVEN context without reviewer
  - WHEN onSubPartDone is called
    - [PASS] THEN commit message omits iteration suffix
- GIVEN git add fails
  - WHEN onSubPartDone is called
    - [PASS] THEN delegates to GitOperationFailureUseCase
    - [PASS] THEN passes correct GitFailureContext
    - [PASS] THEN passes error output to failure use case
- GIVEN git commit fails
  - WHEN onSubPartDone is called
    - [PASS] THEN delegates to GitOperationFailureUseCase
    - [PASS] THEN passes commit error output to failure use case
- GIVEN no changes after sub-part done (empty diff)
  - WHEN onSubPartDone is called
    - [PASS] THEN does not invoke git commit
    - [PASS] THEN skips commit (only 2 git commands executed)
- GIVEN workingDir is specified
  - WHEN onSubPartDone is called
    - [PASS] THEN all git commands include -C <workingDir>
