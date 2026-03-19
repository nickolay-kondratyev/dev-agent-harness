# Refactor: AgentTypeAdapter — Unified Interface

## Summary

Unified `AgentStarter` + `AgentSessionIdResolver` into a single `AgentTypeAdapter` interface (ref.ap.hhP3gT9qK2mR8vNwX5dYa.E) with `ClaudeCodeAdapter` implementation (ref.ap.gCgRdmWd9eTGXPbHJvyxI.E). This eliminates the risk of mismatching starter/resolver pairs and simplifies wiring from two interfaces to one.

## What Was Done

1. **Created `AgentTypeAdapter` interface** in `com.glassthought.shepherd.core.agent.adapter` with:
   - `buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand`
   - `suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String`
   - `BuildStartCommandParams` data class for per-session parameters

2. **Created `ClaudeCodeAdapter`** merging logic from both `ClaudeCodeAgentStarter` and `ClaudeCodeAgentSessionIdResolver`:
   - `buildStartCommand` now takes a `BuildStartCommandParams` data class (per-session config) and embeds bootstrap message as a positional CLI argument with proper shell escaping
   - `resolveSessionId` returns raw `String` session ID (not `ResumableAgentSessionId`)
   - Preserved `unset CLAUDECODE`, GuidScanner abstraction, polling with timeout

3. **Extracted `GuidScanner` interface** into its own file for detekt compliance

4. **Updated `ContextInitializer`**: `ClaudeCodeInfra` now holds `AgentTypeAdapter` instead of `ClaudeCodeAgentSessionIdResolver`

5. **Migrated all 33 tests** into `ClaudeCodeAdapterTest` with adapted assertions (returns `String` not `ResumableAgentSessionId`, `buildStartCommand` takes params). Added new test for bootstrap message shell escaping.

6. **Updated doc references** in `HandshakeGuid`, `ResumableAgentSessionId`, `Constants` to point to new types

7. **Cleaned up**: deleted old files, empty directories, stale detekt baseline entries

## Files Created
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/AgentTypeAdapter.kt` — interface + BuildStartCommandParams data class
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/GuidScanner.kt` — extracted interface
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt` — unified implementation
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapterTest.kt` — combined tests

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` — uses ClaudeCodeAdapter
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt` — updated doc ref
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt` — updated doc ref
- `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt` — updated doc ref
- `detekt-baseline.xml` — regenerated (removed stale entries)

## Files Deleted
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/AgentStarter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/impl/ClaudeCodeAgentStarter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/AgentSessionIdResolver.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/starter/impl/ClaudeCodeAgentStarterTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/sessionresolver/impl/ClaudeCodeAgentSessionIdResolverTest.kt`

## Key Design Decisions

1. **`BuildStartCommandParams` data class** instead of many individual method parameters: The spec shows `buildStartCommand(bootstrapMessage: String)` but the method needs handshakeGuid, workingDir, model, tools, systemPromptFilePath, and appendSystemPrompt which are all per-session. A data class keeps the interface clean while supporting all per-call parameters.

2. **`resolveSessionId` returns `String`** (not `ResumableAgentSessionId`): The caller constructs `ResumableAgentSessionId` with additional context (agentType, model). This keeps the adapter focused on its single responsibility.

3. **Bootstrap message shell escaping**: Added `shellQuote()` that properly escapes `$`, backticks, double quotes, backslashes, and `!` for safe embedding in the shell command.

## Tests
- All tests pass (`./gradlew :app:test` — BUILD SUCCESSFUL)
- Detekt passes with no new issues
