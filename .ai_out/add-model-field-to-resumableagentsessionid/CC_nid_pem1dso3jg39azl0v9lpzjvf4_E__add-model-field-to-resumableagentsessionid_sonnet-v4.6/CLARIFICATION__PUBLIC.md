# CLARIFICATION: Add model field to ResumableAgentSessionId

## Requirements (Confirmed)

1. Add `val model: String` to `ResumableAgentSessionId` data class
2. Populate `model` at creation time (in `ClaudeCodeAgentSessionIdResolver`)
3. Update `ClaudeCodeAgentStarterBundleFactory` to inject model into resolver
4. Update all tests

**Out of scope**: Implementing `ResumeTmuxAgentSessionUseCase` itself (separate ticket `nid_d47u5pku4ldixx23tyggd29ep_E`). This ticket just ensures the model field is present in `ResumableAgentSessionId`.

## Architecture Decision: Constructor Injection into Resolver

**Chosen approach**: Inject `model: String` into `ClaudeCodeAgentSessionIdResolver` constructor.

**Why**:
- `ClaudeCodeAgentStarterBundleFactory.create()` already knows the model (`TEST_MODEL` / `PRODUCTION_MODEL`)  
- It creates `ClaudeCodeAgentSessionIdResolver` immediately after, so can pass model in constructor
- `AgentSessionIdResolver` **interface stays clean** (no model parameter added)
- Natural DI pattern consistent with codebase style
- Minimal surface change

**Flow after change**:
```
ClaudeCodeAgentStarterBundleFactory.create()
  val model = if (environment.isTest) TEST_MODEL else PRODUCTION_MODEL
  ↓
ClaudeCodeAgentSessionIdResolver(claudeProjectsDir, outFactory, model=model)
  ↓ stores model
resolveSessionId(guid)
  ↓
ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId, model=model)
```

## Files to Change

| File | Change |
|------|--------|
| `ResumableAgentSessionId.kt` | Add `val model: String` |
| `ClaudeCodeAgentSessionIdResolver.kt` | Add `model: String` constructor param (both constructors); use in creation |
| `ClaudeCodeAgentStarterBundleFactory.kt` | Refactor to extract model variable, pass to resolver |
| `ClaudeCodeAgentSessionIdResolverTest.kt` | Update all assertions and test constructor calls |
| `SpawnTmuxAgentSessionUseCaseIntegTest.kt` | Add model field assertion |

No other interfaces need changing.
