---
spec: "com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunnerImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a CLAUDE_CODE agent request
  - AND the command is constructed
    - [PASS] THEN it does NOT contain ZAI_API_KEY
    - [PASS] THEN it includes --model with the requested model
    - [PASS] THEN it includes -p with the instructions
    - [PASS] THEN it starts with cd to working directory
  - WHEN run is called
    - [PASS] THEN the request timeout is forwarded to ProcessRunner
    - [PASS] THEN the shell command contains claude --print
- GIVEN a PI agent request
  - AND the command is constructed
    - [PASS] THEN it exports ZAI_API_KEY
    - [PASS] THEN it includes --model with the requested model
    - [PASS] THEN it includes -p with the instructions
    - [PASS] THEN it includes pi --provider zai
    - [PASS] THEN it starts with cd to working directory
- GIVEN instructions with single quotes
  - WHEN the command is constructed
    - [PASS] THEN single quotes are properly escaped
- GIVEN the agent process fails with non-zero exit code
  - WHEN run is called
    - [PASS] THEN exitCode matches the process exit code
    - [PASS] THEN output combines stdout and stderr
    - [PASS] THEN returns Failed result
- GIVEN the agent process succeeds
  - WHEN run is called
    - [PASS] THEN output contains stdout
    - [PASS] THEN returns Success result
- GIVEN the agent process succeeds with both stdout and stderr
  - WHEN run is called
    - [PASS] THEN output combines stdout and stderr
- GIVEN the agent process times out
  - WHEN run is called
    - [PASS] THEN output combines stdout and stderr
    - [PASS] THEN returns TimedOut result
