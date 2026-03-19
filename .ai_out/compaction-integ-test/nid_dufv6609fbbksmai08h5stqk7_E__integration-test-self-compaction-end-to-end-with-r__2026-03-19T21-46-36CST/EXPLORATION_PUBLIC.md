# Exploration: Self-Compaction Integration Test

## Task
Write integration tests for self-compaction E2E flow with real agent session.

## Key Findings

### Integration Test Pattern
- Extend `SharedContextDescribeSpec` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E)
- Gate with `.config(isIntegTestEnabled())`
- Reference: `AgentFacadeImplIntegTest` — sets up real Ktor server, wires `AgentFacadeImpl`, spawns GLM-backed agents
- Uses `ServerPortInjectingAdapter` to inject server port and callback script PATH into tmux session
- Cleanup via `afterEach` (kill sessions) and `afterSpec` (stop server)

### Context Window State Reading
- `ClaudeCodeContextWindowStateReader` reads `${HOME}/.vintrin_env/claude_code/session/<sessionId>/context_window_slim.json`
- JSON: `{ "file_updated_timestamp": "...", "remaining_percentage": 35 }`
- File missing → `ContextWindowStateUnavailableException`
- File stale (>5min) → `ContextWindowState(remainingPercentage = null)`

### Self-Compaction Flow
1. After `AgentSignal.Done`, PartExecutor reads context window state
2. If `remainingPercentage <= 35` → `performCompaction()`
3. Sends compaction instruction → agent writes PRIVATE.md → signals `SelfCompacted`
4. Validates PRIVATE.md exists/non-empty
5. Kills old session → caller spawns new session with PRIVATE.md in instructions

### Test Scenarios (from ticket)
1. `context_window_slim.json` is present and readable after real session starts
2. Self-compaction can be triggered (send compaction instruction, await SelfCompacted signal)
3. Session rotation produces working new session with PRIVATE.md

### Key Classes
- `AgentFacadeImpl` — real facade with signal flow
- `ClaudeCodeContextWindowStateReader` — reads context_window_slim.json
- `SelfCompactionInstructionBuilder` — builds compaction instruction
- `PrivateMdValidator` — validates PRIVATE.md exists and non-empty
- `IntegTestHelpers` — creates system prompts, instruction files, resolves script dirs
