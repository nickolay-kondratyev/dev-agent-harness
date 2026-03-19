---
spec: "com.glassthought.shepherd.core.supporting.git.WorkingTreeValidatorTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN clean working tree (empty porcelain output)
  - WHEN validate is called
    - [PASS] THEN succeeds without throwing
- GIVEN clean working tree (whitespace-only porcelain output)
  - WHEN validate is called
    - [PASS] THEN succeeds without throwing
- GIVEN dirty working tree with mixed changes
  - WHEN validate is called
    - [PASS] THEN error message contains 'Working tree is not clean'
    - [PASS] THEN error message contains instruction to commit or stash
    - [PASS] THEN error message lists all dirty files
- GIVEN dirty working tree with modified files
  - WHEN validate is called
    - [PASS] THEN error message contains the dirty file name
    - [PASS] THEN throws IllegalStateException
- GIVEN dirty working tree with untracked files
  - WHEN validate is called
    - [PASS] THEN error message contains the untracked file name
    - [PASS] THEN throws IllegalStateException
