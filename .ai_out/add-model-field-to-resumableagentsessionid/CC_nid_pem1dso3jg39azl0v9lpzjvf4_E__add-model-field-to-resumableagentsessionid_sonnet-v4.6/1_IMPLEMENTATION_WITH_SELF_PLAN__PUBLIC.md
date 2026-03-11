# IMPLEMENTATION: Add `model` Field to `ResumableAgentSessionId`

## Summary

Added `val model: String` as the 3rd field to `ResumableAgentSessionId`, threaded it through
`ClaudeCodeAgentSessionIdResolver` via constructor injection, and updated the factory and all tests.

## Architecture Decision Followed

Constructor injection into `ClaudeCodeAgentSessionIdResolver`:
- `ClaudeCodeAgentStarterBundleFactory.create()` extracts `model` as a local variable first
- Passes `model` to both `ClaudeCodeAgentStarter` (was already being done inline) and the new `ClaudeCodeAgentSessionIdResolver` constructor parameter
- `AgentSessionIdResolver` interface was NOT changed (no model on the interface method)

## Changes Made

### 1. `ResumableAgentSessionId.kt`
- Added `val model: String` as the 3rd field after `sessionId`
- Updated KDoc to mention the field and its usage for resume (`claude --resume <sessionId> --model <model>`)

### 2. `ClaudeCodeAgentSessionIdResolver.kt`
- Added `private val model: String` to primary constructor (between `outFactory` and `resolveTimeoutMs`)
- Added `model: String` parameter to secondary (test injection) constructor and threaded it into the `this(...)` delegation call
- Updated `ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)` to `ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId, model)`
- Updated KDoc with `@param model` description

### 3. `ClaudeCodeAgentStarterBundleFactory.kt`
- Extracted model selection into `val model = if (environment.isTest) TEST_MODEL else PRODUCTION_MODEL`
- Both `ClaudeCodeAgentStarter` branches now use the shared `model` variable
- `ClaudeCodeAgentSessionIdResolver` now receives `model = model`

### 4. `ClaudeCodeAgentSessionIdResolverTest.kt`
- Added `private const val TEST_MODEL = "test-model-sonnet"` top-level constant
- Updated all 12 `ClaudeCodeAgentSessionIdResolver(...)` instantiations to include `model = TEST_MODEL`
- Updated all `ResumableAgentSessionId` assertions to include the model field

### 5. `SpawnTmuxAgentSessionUseCaseIntegTest.kt`
- Added assertion: `agentSession.resumableAgentSessionId.model.shouldNotBeBlank()`

## Tests

All tests pass: `BUILD SUCCESSFUL` with `bash test.sh`.

## Notes

- The `AgentSessionIdResolver` interface stays clean - model is resolved at construction time, not at call time.
- This change enables future `ResumeTmuxAgentSessionUseCase` (ticket `nid_d47u5pku4ldixx23tyggd29ep_E`) to use `resumableAgentSessionId.model` in the `claude --resume <sessionId> --model <model>` command.
