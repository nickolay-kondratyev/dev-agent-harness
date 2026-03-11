---
closed_iso: 2026-03-11T00:52:36Z
id: nid_uwof1vfp1tcqxzyoh5citkz5o_E
title: "Wire up TmuxAgentSession spawn flow with integration test"
status: closed
deps: []
links: []
created_iso: 2026-03-11T00:10:10Z
status_updated_iso: 2026-03-11T00:52:36Z
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

## Resolution

### Implementation Complete

**All 28 unit tests pass + 1 integration test verified.**

**New files created** (in `com.glassthought.chainsaw.core`):
- `data/PhaseType.kt` — Enum: IMPLEMENTOR, REVIEWER, PLANNER, PLAN_REVIEWER
- `agent/data/TmuxStartCommand.kt` — @JvmInline value class
- `agent/data/StartAgentRequest.kt` — Data class (phaseType + workingDir)
- `agent/TmuxAgentSession.kt` — Data class (TmuxSession + ResumableAgentSessionId)
- `agent/starter/AgentStarter.kt` — Interface: buildStartCommand()
- `agent/starter/impl/ClaudeCodeAgentStarter.kt` — Builds `claude` CLI with bash -c wrapper
- `agent/AgentTypeChooser.kt` — Interface + DefaultAgentTypeChooser (CLAUDE_CODE in V1)
- `agent/AgentStarterBundle.kt` — Data class (AgentStarter + AgentSessionIdResolver)
- `agent/AgentStarterBundleFactory.kt` — Interface
- `agent/impl/ClaudeCodeAgentStarterBundleFactory.kt` — Test vs production config via Environment
- `agent/SpawnTmuxAgentSessionUseCase.kt` — Orchestrates full spawn flow with GUID handshake
- `config/prompts/test-agent-system-prompt.txt` — Minimal test prompt

**Modified**:
- `initializer/data/Environment.kt` — Added `fun test()` factory method

**Key discoveries during implementation**:
- Claude CLI uses `--system-prompt` (inline text), NOT `--system-prompt-file`
- `CLAUDECODE` env var must be unset to avoid nested session detection
- 5-second configurable startup delay needed before GUID handshake

**Deferred**: ResumeTmuxAgentSessionUseCase → ticket nid_d47u5pku4ldixx23tyggd29ep_E
