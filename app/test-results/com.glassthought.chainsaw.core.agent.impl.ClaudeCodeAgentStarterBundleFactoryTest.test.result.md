---
spec: "com.glassthought.chainsaw.core.agent.impl.ClaudeCodeAgentStarterBundleFactoryTest"
status: PASSED
failed: 0
skipped: 0
---

- (1) GIVEN factory with test environment
  - WHEN create is called with PI agent type
    - [PASS] THEN throws IllegalArgumentException
- GIVEN factory with null systemPromptFilePath
  - WHEN create is called with CLAUDE_CODE
    - [PASS] THEN start command has correct structure
- GIVEN factory with production environment
  - WHEN create is called with CLAUDE_CODE
    - [PASS] THEN start command uses --append-system-prompt-file
    - [PASS] THEN start command uses full allowed tools set
- GIVEN factory with test environment
  - WHEN create is called with CLAUDE_CODE
    - [PASS] THEN start command includes --dangerously-skip-permissions
    - [PASS] THEN start command includes cd to working dir
    - [PASS] THEN start command uses --system-prompt-file (not append)
    - [PASS] THEN start command uses minimal allowed tools
    - [PASS] THEN start command uses sonnet model
    - [PASS] THEN starter is a ClaudeCodeAgentStarter
