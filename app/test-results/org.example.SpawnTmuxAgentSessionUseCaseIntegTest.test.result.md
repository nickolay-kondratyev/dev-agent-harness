---
spec: "org.example.SpawnTmuxAgentSessionUseCaseIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN SpawnTmuxAgentSessionUseCase with test configuration
  - WHEN spawn is called with IMPLEMENTOR phase
    - [PASS] THEN returns a TmuxAgentSession with valid tmux session and resolved session ID
