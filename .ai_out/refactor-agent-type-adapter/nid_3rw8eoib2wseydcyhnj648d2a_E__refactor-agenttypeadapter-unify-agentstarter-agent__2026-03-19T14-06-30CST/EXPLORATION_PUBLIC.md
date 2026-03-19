# Exploration: AgentTypeAdapter Refactor

## Current State
- `AgentStarter` interface at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/AgentStarter.kt` — `fun buildStartCommand(): TmuxStartCommand`
- `AgentSessionIdResolver` interface at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/AgentSessionIdResolver.kt` — `suspend fun resolveSessionId(guid, model): ResumableAgentSessionId`
- `ClaudeCodeAgentStarter` implementation — builds `claude` CLI command with model, tools, system-prompt, permissions, CLAUDECODE unset, handshake GUID export
- `ClaudeCodeAgentSessionIdResolver` implementation (ref.ap.gCgRdmWd9eTGXPbHJvyxI.E) — polls JSONL files for GUID, returns `ResumableAgentSessionId`
- Neither interface has production callers yet — only used in tests
- 33 passing tests across both implementations

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/AgentStarter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/impl/ClaudeCodeAgentStarter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/AgentSessionIdResolver.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` — wires `ClaudeCodeAgentSessionIdResolver` into infra
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/starter/impl/ClaudeCodeAgentStarterTest.kt` — 15 tests
- `app/src/test/kotlin/com/glassthought/shepherd/core/sessionresolver/impl/ClaudeCodeAgentSessionIdResolverTest.kt` — 18 tests
- `app/src/test/kotlin/com/glassthought/bucket/TmuxSessionManagerIntegTest.kt` — uses TmuxStartCommand("bash"), no agent-specific references

## Target State (from spec at ref.ap.A0L92SUzkG3gE0gX04ZnK.E)
```kotlin
interface AgentTypeAdapter {
    fun buildStartCommand(bootstrapMessage: String): TmuxStartCommand
    suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String
}
```
- `buildStartCommand` now accepts `bootstrapMessage` — embedded as positional CLI argument
- `resolveSessionId` returns `String` (just session ID), caller constructs `ResumableAgentSessionId`
- `ClaudeCodeAdapter` implements both methods by merging existing logic
- `ClaudeCodeInfra` in ContextInitializer updated to hold adapter

## Signature Changes
1. `buildStartCommand()` → `buildStartCommand(bootstrapMessage: String)` — bootstrap message as positional arg to `claude` CLI
2. `resolveSessionId(guid, model)` → `resolveSessionId(handshakeGuid)` returning `String` — drops `model` param, returns raw session ID
