# EXPLORATION: Add `model` Field to `ResumableAgentSessionId`

## Current Structure

**File**: `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt`

```kotlin
data class ResumableAgentSessionId(
    val agentType: AgentType,
    val sessionId: String,
)
```

## Instantiation Points

### Primary Creator: `ClaudeCodeAgentSessionIdResolver`
- File: `...core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt` line 125
- Creates: `ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)`
- **Problem**: At this point, NO access to the model that was used to spawn the session

### Other Usage
- `SpawnTmuxAgentSessionUseCase` (line 101) - returns `TmuxAgentSession` containing `resumableAgentSessionId`
- Integration test: `SpawnTmuxAgentSessionUseCaseIntegTest.kt` lines 56-57

## Model Source

- `ClaudeCodeAgentStarterBundleFactory` creates `ClaudeCodeAgentStarter(model="sonnet", ...)`
- Model constants: `TEST_MODEL = "sonnet"`, `PRODUCTION_MODEL = "sonnet"`
- Model is passed to `ClaudeCodeAgentStarter` at construction time
- Model is used in `buildStartCommand()` → `--model sonnet`
- **Model is lost/inaccessible** by the time resolver creates `ResumableAgentSessionId`

## Data Flow

```
ClaudeCodeAgentStarterBundleFactory.create()
  ↓ Creates ClaudeCodeAgentStarter(model="sonnet")
SpawnTmuxAgentSessionUseCase.spawn()
  ↓ Calls starter.buildStartCommand() → "--model sonnet" in tmux
  ↓ Calls bundle.sessionIdResolver.resolveSessionId(guid)
ClaudeCodeAgentSessionIdResolver
  ↓ Finds sessionId from JSONL filename
  ↓ Creates ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId) ← MODEL LOST
Returns TmuxAgentSession(tmuxSession, resumableAgentSessionId)
```

## `ResumeTmuxAgentSessionUseCase`

**STATUS: NOT YET IMPLEMENTED** - tracked in ticket `nid_d47u5pku4ldixx23tyggd29ep_E`.

Planned: `claude --resume <sessionId> --model <model>` (needs model field from `ResumableAgentSessionId`).

## Tests to Update

1. `ClaudeCodeAgentSessionIdResolverTest.kt` - all `ResumableAgentSessionId` assertions
2. `SpawnTmuxAgentSessionUseCaseIntegTest.kt` - model field verification

## Architecture Options for Threading Model

**Option A (Preferred)**: Pass model through `AgentSessionIdResolver` interface
- `resolveSessionId(guid: String, model: String): ResumableAgentSessionId`
- Caller (`SpawnTmuxAgentSessionUseCase`) passes model it obtained from `AgentStarterBundle`

**Option B**: Inject model into resolver constructor at bundle creation time
- `ClaudeCodeAgentSessionIdResolver` gets model in constructor
- Bundle factory passes model to resolver when constructing bundle

Both approaches require `SpawnTmuxAgentSessionUseCase` or `AgentStarterBundle` to expose the model. Need to check `AgentStarterBundle` structure.

## Key Files

| File | Action |
|------|--------|
| `...core/agent/sessionresolver/ResumableAgentSessionId.kt` | Add `val model: String` |
| `...core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt` | Pass model when creating |
| `...core/useCase/SpawnTmuxAgentSessionUseCase.kt` | Pass model to resolver |
| `...core/agent/impl/ClaudeCodeAgentStarterBundle*.kt` | Expose model |
| Tests for all above | Update assertions |
