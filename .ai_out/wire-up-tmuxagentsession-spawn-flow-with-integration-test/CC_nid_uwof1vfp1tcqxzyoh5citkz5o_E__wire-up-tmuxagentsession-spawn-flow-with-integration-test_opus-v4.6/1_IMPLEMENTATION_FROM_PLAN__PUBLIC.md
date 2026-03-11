# Implementation Summary: Wire up TmuxAgentSession Spawn Flow

## What Was Built

End-to-end TmuxAgentSession spawn flow: given a `StartAgentRequest`, the system chooses an agent type, builds the Claude CLI command, creates a tmux session, performs a GUID handshake to discover the agent's session ID, and returns a `TmuxAgentSession` that bundles the live tmux handle with the resumable session identity.

## New Files Created

### Data Types (Phase 1)
| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/data/PhaseType.kt` | Enum: IMPLEMENTOR, REVIEWER, PLANNER, PLAN_REVIEWER |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/data/TmuxStartCommand.kt` | @JvmInline value class wrapping shell command string |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/data/StartAgentRequest.kt` | Input data class: phaseType + workingDir |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/TmuxAgentSession.kt` | Data class pairing TmuxSession + ResumableAgentSessionId |

### Agent Starter (Phase 2)
| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/AgentStarter.kt` | Interface: buildStartCommand() -> TmuxStartCommand |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarter.kt` | Builds `claude` CLI command with model, tools, system prompt, permissions flags |

### Agent Type Chooser (Phase 3)
| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentTypeChooser.kt` | Interface + DefaultAgentTypeChooser (always CLAUDE_CODE in V1) |

### Bundle and Factory (Phase 4)
| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentStarterBundle.kt` | Data class pairing AgentStarter + AgentSessionIdResolver |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentStarterBundleFactory.kt` | Interface: create(agentType, request) -> AgentStarterBundle |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactory.kt` | Implementation: reads system prompt file, creates starter+resolver bundles |

### Orchestrator (Phase 7)
| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCase.kt` | Full spawn flow: GUID generation -> type selection -> command build -> tmux session -> GUID handshake -> return TmuxAgentSession |

### System Prompt (Phase 6)
| File | Description |
|------|-------------|
| `config/prompts/test-agent-system-prompt.txt` | Minimal prompt for test agent sessions |

### Modified Files
| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt` | Added `fun test(): Environment` factory method |

### Test Files
| File | Type |
|------|------|
| `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarterTest.kt` | Unit test: 11 test cases |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/DefaultAgentTypeChooserTest.kt` | Unit test: data-driven across all PhaseType values |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactoryTest.kt` | Unit test: test/prod config, error handling |
| `app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt` | Integration test: full spawn flow with real Claude Code |

## Key Design Decisions

### 1. TmuxAgentSession is a plain data class (not interface)
Per review feedback. Both spawn and resume produce the same data shape. Interface can be extracted later if polymorphism is needed.

### 2. Factory takes full StartAgentRequest (not just PhaseType)
Because the factory needs `workingDir` to construct the `ClaudeCodeAgentStarter`. The concrete justification is that the working directory is part of the CLI command.

### 3. ResumeTmuxAgentSessionUseCase deferred
Created follow-up ticket `nid_d47u5pku4ldixx23tyggd29ep_E`. The plan's Phase 8 was under-specified per review feedback. Better to design it properly in its own ticket.

### 4. CLI flags corrected from plan
- Plan proposed `--system-prompt-file` and `--append-system-prompt-file` (do not exist in Claude CLI v2.1.63)
- Corrected to `--system-prompt` and `--append-system-prompt` which take inline text
- Factory reads the prompt file and passes content string to the starter

### 5. CLAUDECODE env var must be unset
Claude Code refuses to start when the `CLAUDECODE` environment variable is set (nested session detection). The harness spawns Claude in a tmux session which inherits the parent environment, so `unset CLAUDECODE` is prepended to the bash command.

### 6. Agent startup delay added
5-second configurable delay between tmux session creation and GUID sendKeys. Claude CLI needs time to initialize its interactive prompt. Without this, the GUID would be consumed by bash rather than Claude's input handler.

### 7. No unit test for SpawnTmuxAgentSessionUseCase
Per review feedback: thin orchestrator with trivial logic (call A, then B, then C). Integration test is the real verification. A unit test would only test mocks.

### 8. Integration test uses single `it` block
Spawning Claude sessions is expensive (API cost + time). Multiple assertions verify different facets of the same result in one test.

## Test Results

- **Unit tests**: 23 new tests, all passing
- **Integration test**: 1 test, passing (full tmux + Claude Code + GUID handshake)
- **Existing tests**: All passing (no regressions)
