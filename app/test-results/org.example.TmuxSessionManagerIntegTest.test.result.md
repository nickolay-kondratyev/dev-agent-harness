---
spec: "org.example.TmuxSessionManagerIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN TmuxSessionManager
  - WHEN createSession with bash
    - [PASS] THEN session exists
  - WHEN killSession is called on existing session
    - [PASS] THEN session no longer exists
  - WHEN sessionExists with existing session
    - [PASS] THEN returns true
  - WHEN sessionExists with non-existent name
    - [PASS] THEN returns false
