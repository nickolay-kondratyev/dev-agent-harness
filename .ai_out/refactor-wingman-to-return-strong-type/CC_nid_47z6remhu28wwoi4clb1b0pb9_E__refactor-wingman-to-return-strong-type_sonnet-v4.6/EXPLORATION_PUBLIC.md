# Exploration: Refactor Wingman to Return Strong Type

## Summary of Findings

### Wingman Interface (current)
File: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt`
- `ap.D3ICqiFdFFgbFIPLMTYdoyss.E`
- Single method: `suspend fun resolveSessionId(guid: HandshakeGuid): String`

### Only Implementation
File: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`
- `ap.gCgRdmWd9eTGXPbHJvyxI.E`
- Scans JSONL files in `~/.claude/projects` for GUID
- Returns `matchingFiles.single().nameWithoutExtension` as a String (UUID format e.g. `77d5b7ea-cf04-453b-8867-162404763e18`)

### Existing Related Types in wingman package
- `HandshakeGuid.kt` — `@JvmInline value class HandshakeGuid(val value: String)` — same package
- No existing `AgentType` enum

### Package location
`app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/` — place new types here.

### Test file
`app/src/test/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingmanTest.kt`
- 12+ call sites of `resolveSessionId`, all expecting `String` return
- Will need updating to use `ResumableAgentSessionId`

### Production call sites
Currently **no production code** calls `resolveSessionId` — only tests. Safe to refactor.

## Implementation Plan
1. Create `AgentType.kt` enum with `CLAUDE_CODE` and `PI`
2. Create `ResumableAgentSessionId.kt` data class: `data class ResumableAgentSessionId(val agentType: AgentType, val sessionId: String)`
3. Update `Wingman.kt` interface return type
4. Update `ClaudeCodeWingman.kt` to return `ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)`
5. Update all tests to use new type

## No Ambiguities
- Ticket explicitly says "data class" (not value class)
- `AgentType` enum values are given: `CLAUDE_CODE`, `PI`
- No other production callers to worry about
