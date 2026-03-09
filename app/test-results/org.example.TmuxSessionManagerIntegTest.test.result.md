---
spec: "org.example.TmuxSessionManagerIntegTest"
status: FAILED
failed: 2
skipped: 0
---

## Failed Tests

- GIVEN TmuxSessionManager > WHEN session is created > THEN exists() returns true
  - Error: Cannot run program "tmux": Exec failed, error: 2 (No such file or directory) 
- GIVEN TmuxSessionManager > WHEN killSession is called on existing session > THEN exists() returns false
  - Error: Cannot run program "tmux": Exec failed, error: 2 (No such file or directory) 

- GIVEN TmuxSessionManager
  - WHEN killSession is called on existing session
    - [FAIL] THEN exists() returns false
  - WHEN session is created
    - [FAIL] THEN exists() returns true
