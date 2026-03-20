---
spec: "com.glassthought.bucket.TmuxSessionManagerIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN TmuxSessionManager
  - WHEN killSession is called on existing session
    - [PASS] THEN exists() returns false
  - WHEN session is created
    - [PASS] THEN exists() returns true
