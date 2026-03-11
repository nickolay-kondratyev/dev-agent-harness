---
spec: "com.glassthought.ticketShepherd.core.agent.impl.ClaudeCodeAgentStarterBundleFactoryTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN factory with production environment and a system prompt file
  - WHEN create is called with CLAUDE_CODE
    - [PASS] THEN start command uses --append-system-prompt-file
    - [PASS] THEN start command uses --tools with full production tool set
- GIVEN factory with test environment and a system prompt file
  - WHEN create is called with CLAUDE_CODE
    - [PASS] THEN start command contains the file path
    - [PASS] THEN start command includes --dangerously-skip-permissions
    - [PASS] THEN start command includes cd to working dir
    - [PASS] THEN start command uses --system-prompt-file (not append)
    - [PASS] THEN start command uses --tools with test tool set
    - [PASS] THEN start command uses sonnet model
    - [PASS] THEN starter is a ClaudeCodeAgentStarter
- GIVEN factory with test environment and no system prompt file
  - WHEN create is called with CLAUDE_CODE
    - [PASS] THEN start command does not contain --system-prompt-file
    - [PASS] THEN start command uses sonnet model
  - WHEN create is called with PI agent type
    - [PASS] THEN throws IllegalArgumentException
