---
spec: "com.glassthought.shepherd.core.agent.tmux.util.ProcessResultTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN ProcessResult.orThrow
  - WHEN exitCode is 0
    - [PASS] THEN no exception is thrown
  - WHEN exitCode is non-zero
    - [PASS] THEN IllegalStateException is thrown with expected message
