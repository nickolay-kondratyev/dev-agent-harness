---
spec: "com.glassthought.shepherd.usecase.spawn.SpawnTmuxAgentSessionUseCaseTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN BuildStartCommandParams construction
  - WHEN execute is called
    - [PASS] THEN passes correct bootstrapMessage to adapter
    - [PASS] THEN passes correct model to adapter
    - [PASS] THEN passes correct systemPromptFilePath to adapter
    - [PASS] THEN passes correct tools to adapter
    - [PASS] THEN passes correct workingDir to adapter
    - [PASS] THEN passes handshakeGuid starting with 'handshake.' to adapter
- GIVEN TMUX session name format
  - WHEN execute is called
    - [PASS] THEN TMUX session is created with name 'shepherd_partName_subPartName'
- GIVEN TmuxSessionCreator.createSession throws
  - WHEN execute is called
    - [PASS] THEN TmuxSessionCreationException contains the session name
    - [PASS] THEN TmuxSessionCreationException wraps the original cause
    - [PASS] THEN throws TmuxSessionCreationException
- GIVEN happy path — session record storage
  - WHEN execute completes successfully
    - [PASS] THEN session record handshakeGuid starts with 'handshake.'
    - [PASS] THEN session record has correct agentType
    - [PASS] THEN session record has correct model
    - [PASS] THEN session record has correct timestamp from clock
    - [PASS] THEN session record has resolved session ID in agentSession
    - [PASS] THEN session record is added to CurrentState
- GIVEN happy path — startedDeferred completes immediately
  - WHEN execute is called
    - [PASS] THEN returns TmuxAgentSession with correct pane target
    - [PASS] THEN returns TmuxAgentSession with correct session name
    - [PASS] THEN returns resumableAgentSessionId with correct agentType
    - [PASS] THEN returns resumableAgentSessionId with correct model
    - [PASS] THEN returns resumableAgentSessionId with handshakeGuid starting with 'handshake.'
    - [PASS] THEN returns resumableAgentSessionId with resolved sessionId
- GIVEN no adapter registered for the agent type
  - WHEN execute is called with PI agent type
    - [PASS] THEN throws IllegalArgumentException mentioning PI
- GIVEN startedDeferred completes asynchronously within timeout
  - WHEN execute is called
    - [PASS] THEN returns successfully
- GIVEN startedDeferred never completes (startup timeout)
  - WHEN execute is called
    - [PASS] THEN StartupTimeoutException contains the session name
    - [PASS] THEN StartupTimeoutException includes the timeout duration
    - [PASS] THEN throws StartupTimeoutException
