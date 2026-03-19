---
id: nid_njq7ezzxmf8orffmzf7oorsd0_E
title: "Implement NonInteractiveAgentRunner — interface, data classes, and implementation"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T23:55:59Z
status_updated_iso: 2026-03-19T17:07:10Z
type: feature
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [agent, noninteractive]
---

Implement the NonInteractiveAgentRunner component per spec at doc/core/NonInteractiveAgentRunner.md (ref.ap.ad4vG4G2xMPiMHRreoYVr.E).

This is a lightweight subprocess-based agent invocation for utility tasks that don't need interactive TMUX sessions.

## Scope

### Interface + Data Classes
- `NonInteractiveAgentRunner` interface with single `suspend fun run(request: NonInteractiveAgentRequest): NonInteractiveAgentResult`
- `NonInteractiveAgentRequest` data class: instructions (String), workingDirectory (Path), agentType (AgentType), model (String), timeout (Duration)
- `NonInteractiveAgentResult` sealed class: Success(output), Failed(exitCode, output), TimedOut(output)

### Implementation (NonInteractiveAgentRunnerImpl)
- **`ProcessRunner` must be a constructor parameter** for testability (injected, not created internally)
- Command construction per AgentType:
  - ClaudeCode: `claude --print --model {model} -p "{instructions}"`
  - PI: `pi --provider zai --model {model} -p "{instructions}"` (with ZAI_API_KEY env var)
- Spawn subprocess via ProcessRunner with working directory, inherited env vars + ZAI_API_KEY for PI
- Combined stdout+stderr capture
- Await process completion with timeout
- Exit code 0 → Success, non-zero → Failed, timeout → kill + TimedOut
- ZAI_API_KEY derived from ${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN at initialization

### Testing
- Unit tests with a fake/mock ProcessRunner verifying:
  - Correct command construction for ClaudeCode
  - Correct command construction for PI (including ZAI_API_KEY)
  - Success/Failed/TimedOut result mapping from process exit
  - Timeout kills the process

### Package
- `com.glassthought.shepherd.core.agent.noninteractive`

### Key references
- Uses existing `AgentType` enum at app/src/main/kotlin/com/glassthought/shepherd/core/data/AgentType.kt
- Uses existing ProcessRunner from asgardCore
- Spec: doc/core/NonInteractiveAgentRunner.md

