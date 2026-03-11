---
spec: "com.glassthought.chainsaw.core.agent.starter.impl.ClaudeCodeAgentStarterTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN ClaudeCodeAgentStarter with all flags enabled
  - WHEN buildStartCommand is called
    - [PASS] THEN command contains --allowedTools Read,Write
    - [PASS] THEN command contains --dangerously-skip-permissions
    - [PASS] THEN command contains --model sonnet
    - [PASS] THEN command contains --system-prompt-file
    - [PASS] THEN command starts with bash -c and includes cd to working dir
- GIVEN ClaudeCodeAgentStarter with appendSystemPrompt=true
  - WHEN buildStartCommand is called
    - [PASS] THEN command contains --append-system-prompt-file
    - [PASS] THEN command does NOT contain --system-prompt-file (without append prefix)
- GIVEN ClaudeCodeAgentStarter with empty allowedTools
  - WHEN buildStartCommand is called
    - [PASS] THEN command does not contain --allowedTools
- GIVEN ClaudeCodeAgentStarter without system prompt file
  - WHEN buildStartCommand is called
    - [PASS] THEN command does not contain --append-system-prompt-file
    - [PASS] THEN command does not contain --dangerously-skip-permissions
    - [PASS] THEN command does not contain --system-prompt-file
