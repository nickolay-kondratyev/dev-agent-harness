---
spec: "com.glassthought.shepherd.usecase.healthmonitoring.AgentUnresponsiveUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN NO_ACTIVITY_TIMEOUT detection context
  - WHEN handle is called
    - [PASS] THEN does NOT kill the session
    - [PASS] THEN returns PingSent result
    - [PASS] THEN sends a health ping via sendRawKeys
    - [PASS] THEN sends ping to the correct pane target
- GIVEN PING_TIMEOUT detection context
  - WHEN handle is called
    - [PASS] THEN includes session name in Crashed signal details
    - [PASS] THEN kills the TMUX session
    - [PASS] THEN returns Crashed signal with PING_TIMEOUT context in details
    - [PASS] THEN returns SessionKilled result
- GIVEN STARTUP_TIMEOUT detection context
  - WHEN handle is called
    - [PASS] THEN includes handshakeGuid in Crashed signal details
    - [PASS] THEN includes session name in Crashed signal details
    - [PASS] THEN kills the TMUX session
    - [PASS] THEN kills the correct session
    - [PASS] THEN returns Crashed signal with STARTUP_TIMEOUT context in details
    - [PASS] THEN returns SessionKilled result
- GIVEN different diagnostic values
  - WHEN handle is called with PING_TIMEOUT
    - [PASS] THEN stale duration is included in Crashed signal details
  - WHEN handle is called with STARTUP_TIMEOUT
    - [PASS] THEN timeout duration is included in Crashed signal details
- GIVEN session killer throws an exception
  - WHEN handle is called with STARTUP_TIMEOUT
    - [PASS] THEN the exception bubbles up
