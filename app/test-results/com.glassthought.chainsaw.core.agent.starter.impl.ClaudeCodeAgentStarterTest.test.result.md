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
    - [PASS] THEN command contains --system-prompt with the prompt text
    - [PASS] THEN command starts with bash -c and includes cd to working dir
- GIVEN ClaudeCodeAgentStarter with appendSystemPrompt=true
  - WHEN buildStartCommand is called
    - [PASS] THEN command contains --append-system-prompt
    - [PASS] THEN command does NOT contain bare --system-prompt (without append prefix)
- GIVEN ClaudeCodeAgentStarter with empty allowedTools
  - WHEN buildStartCommand is called
    - [PASS] THEN command does not contain --allowedTools
- GIVEN ClaudeCodeAgentStarter with system prompt containing single quotes
  - WHEN buildStartCommand is called
    - [PASS] THEN single quotes in the prompt are escaped for the outer bash -c wrapper
    - [PASS] THEN the command is a valid bash -c wrapper with proper start and end quotes
    - [PASS] THEN the prompt is still double-quoted within the inner command
- GIVEN ClaudeCodeAgentStarter with workingDir containing single quote
  - WHEN buildStartCommand is called
    - [PASS] THEN single quote in workingDir is properly escaped
- GIVEN ClaudeCodeAgentStarter without system prompt
  - WHEN buildStartCommand is called
    - [PASS] THEN command does not contain --append-system-prompt
    - [PASS] THEN command does not contain --dangerously-skip-permissions
    - [PASS] THEN command does not contain --system-prompt
