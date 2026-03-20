---
spec: "com.glassthought.shepherd.core.git.GitBranchManagerIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a fresh git repo
  - WHEN createAndCheckout is called with 'feature__test__try-1'
    - [PASS] THEN getCurrentBranch returns 'feature__test__try-1'
  - WHEN createAndCheckout is called with a blank name
    - [PASS] THEN throws IllegalArgumentException
  - WHEN createAndCheckout is called with an already-existing branch name
    - [PASS] THEN throws RuntimeException
  - WHEN getCurrentBranch is called
    - [PASS] THEN returns 'main'
