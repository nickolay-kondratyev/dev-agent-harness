---
id: nid_uwof1vfp1tcqxzyoh5citkz5o_E
title: "Wire up TmuxAgentSession spawn flow with integration test"
status: in_progress
deps: []
links: []
created_iso: 2026-03-11T00:10:10Z
status_updated_iso: 2026-03-11T00:11:12Z
type: feature
priority: 2
assignee: nickolaykondratyev
tags: [tmux, agent, integration-test]
---

## Goal

Implement the TmuxAgentSession spawn flow end-to-end and prove it works with an integration test that performs session resolution (AgentSessionIdResolver, formerly Wingman).

## Design (from design discussion on ticket nid_j54dq6ra33hix1e8aavanb8bz_E)

### Core Abstractions

**TmuxAgentSession (interface)**
- Carries: TmuxSession (live tmux handle) + ResumableAgentSession (session ID + agent type)
- Created via SpawnTmuxAgentSessionUseCase (new agent) or ResumeTmuxAgentSessionUseCase (resume)

**StartAgentRequest**
- Input to SpawnTmuxAgentSessionUseCase
- Contains: phaseType: PhaseType
- Extensible — future fields: agentType override, model preference, etc.

**AgentTypeChooser (interface)**
- fun choose(request: StartAgentRequest): AgentType
- Decouples agent type selection from the spawn use case

**AgentStarterBundle**
- val starter: AgentStarter
- val sessionIdResolver: AgentSessionIdResolver (renamed from Wingman)

**AgentStarterBundleFactory (interface)**
- fun create(agentType: AgentType, phaseType: PhaseType): AgentStarterBundle
- sessionIdResolver is agent-type-scoped (ignores phaseType internally)
- AgentStarter is phase-sensitive (different --tools, --system-prompt per phase)

**AgentStarter (interface)**
- fun buildStartCommand(context: ...): TmuxStartCommand
- ClaudeCodeAgentStarter produces `claude --tools "..." --system-prompt-file <FILE> --model sonnet` etc.

**SpawnTmuxAgentSessionUseCase**
- Dependencies: AgentTypeChooser, AgentStarterBundleFactory
- Flow:
  1. agentTypeChooser.choose(request) -> AgentType
  2. bundleFactory.create(agentType, request.phaseType) -> AgentStarterBundle
  3. bundle.starter.buildStartCommand(...) -> tmux start command
  4. Start tmux session with that command
  5. bundle.sessionIdResolver.resolveSessionId(guid) -> session ID
  6. Return TmuxAgentSession

**ResumeTmuxAgentSessionUseCase** — takes ResumableAgentSession, creates TmuxSession, starts agent with `--resume`, returns TmuxAgentSession. No AgentSessionIdResolver needed.

### Integration Test Requirements

- Test the full spawn flow: create tmux session -> start Claude Code -> Wingman/AgentSessionIdResolver GUID handshake -> resolve session ID -> return TmuxAgentSession
- Use **Environment.isTest == true** to trigger a slim test configuration:
  - `--system-prompt-file <FILE>` with a minimal system prompt (to minimize context usage)
  - `--tools Read,Write` (minimal tool set to reduce context)
  - `--model sonnet`
- Integration test gated with `isIntegTestEnabled()` as per project standards
- Verify TmuxAgentSession contains valid TmuxSession + resolved session ID

### Agent Types

Starting agent types: ClaudeCode, PI agent.

### Key Files

- AgentSessionIdResolver interface + ClaudeCodeAgentSessionIdResolver: app/src/main/kotlin/com/glassthought/chainsaw/wingman/ (already exists, recently renamed from Wingman)
- TmuxSessionManager: app/src/main/kotlin/com/glassthought/chainsaw/tmux/
- Environment: app/src/main/kotlin/com/glassthought/chainsaw/initializer/
- Integration test support: app/src/test/kotlin/org/example/integTestSupport.kt

