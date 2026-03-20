---
spec: "com.glassthought.shepherd.core.supporting.git.GitStagingCommitHelperTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN workingDir is specified
  - [PASS] THEN commit prepends -C <dir> to the command
  - [PASS] THEN hasStagedChanges prepends -C <dir> to the command
  - [PASS] THEN stageAll prepends -C <dir> to the command
- commit
  - GIVEN git commit fails
    - [PASS] THEN delegates to GitOperationFailureUseCase
    - [PASS] THEN passes error output to failure use case
    - [PASS] THEN passes the provided failure context
  - GIVEN git commit succeeds
    - [PASS] THEN executes the commit command with provided args
  - GIVEN git commit with author succeeds
    - [PASS] THEN includes author in command
- hasStagedChanges
  - GIVEN git diff --cached --quiet exits 0 (no changes)
    - [PASS] THEN returns false
  - GIVEN git diff --cached --quiet exits non-zero (changes exist)
    - [PASS] THEN returns true
- stageAll
  - GIVEN git add -A fails
    - [PASS] THEN delegates to GitOperationFailureUseCase with correct command
    - [PASS] THEN passes error output to failure use case
    - [PASS] THEN passes the provided failure context
  - GIVEN git add -A succeeds
    - [PASS] THEN executes git add -A
