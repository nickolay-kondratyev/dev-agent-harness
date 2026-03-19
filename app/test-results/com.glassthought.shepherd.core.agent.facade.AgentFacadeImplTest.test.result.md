---
spec: "com.glassthought.shepherd.core.agent.facade.AgentFacadeImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN killSession with existing entry
  - WHEN killSession is called
    - [PASS] THEN calls sessionKiller
    - [PASS] THEN removes entry from SessionsState
- GIVEN killSession with non-existing entry
  - WHEN killSession is called
    - [PASS] THEN does not call sessionKiller
    - [PASS] THEN does not throw
- GIVEN readContextWindowState
  - WHEN called with a valid handle
    - [PASS] THEN delegates with correct sessionId
    - [PASS] THEN returns the reader result
- GIVEN sendPayloadAndAwaitSignal
  - WHEN session entry does not exist
    - [PASS] THEN throws IllegalStateException
  - WHEN signal deferred is completed
    - [PASS] THEN returns the completed signal
- GIVEN spawnAgent startup timeout
  - WHEN startup signal never arrives
    - [PASS] THEN cleans up SessionsState
    - [PASS] THEN kills the TMUX session
    - [PASS] THEN throws AgentSpawnException
- GIVEN spawnAgent with startup completing
  - WHEN spawnAgent is called
    - [PASS] THEN calls adapter.buildStartCommand once
    - [PASS] THEN calls adapter.resolveSessionId
    - [PASS] THEN creates TMUX session with correct name
    - [PASS] THEN passes correct bootstrapMessage
    - [PASS] THEN passes correct model
    - [PASS] THEN passes correct systemPromptFilePath
    - [PASS] THEN passes handshakeGuid with prefix
    - [PASS] THEN registers entry in SessionsState
    - [PASS] THEN returns handle with correct agentType
    - [PASS] THEN returns handle with guid prefix
    - [PASS] THEN returns handle with resolved sessionId
    - [PASS] THEN returns handle with timestamp from clock
