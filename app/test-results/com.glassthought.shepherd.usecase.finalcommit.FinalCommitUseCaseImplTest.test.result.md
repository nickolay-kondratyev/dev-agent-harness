---
spec: "com.glassthought.shepherd.usecase.finalcommit.FinalCommitUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN git add fails
  - WHEN commitIfDirty is called
    - [PASS] THEN delegates to GitOperationFailureUseCase
    - [PASS] THEN passes correct GitFailureContext
    - [PASS] THEN passes error output to failure use case
- GIVEN git commit fails
  - WHEN commitIfDirty is called
    - [PASS] THEN delegates to GitOperationFailureUseCase
    - [PASS] THEN passes commit error output to failure use case
    - [PASS] THEN passes correct GitFailureContext for commit failure
- GIVEN working tree has uncommitted changes
  - WHEN commitIfDirty is called
    - [PASS] THEN checks for staged changes via git diff --cached --quiet
    - [PASS] THEN creates commit with shepherd final-state-commit message
    - [PASS] THEN executes exactly 3 git commands
    - [PASS] THEN executes git add -A first
- GIVEN working tree is clean (no changes)
  - WHEN commitIfDirty is called
    - [PASS] THEN does not invoke git commit
    - [PASS] THEN skips commit (only 2 git commands executed)
