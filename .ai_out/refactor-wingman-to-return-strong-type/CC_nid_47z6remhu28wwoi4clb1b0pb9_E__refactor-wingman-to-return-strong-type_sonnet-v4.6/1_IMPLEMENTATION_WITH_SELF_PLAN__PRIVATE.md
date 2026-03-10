# Implementation Plan: Refactor Wingman to Return Strong Type

## Goal
Change `Wingman.resolveSessionId` to return `ResumableAgentSessionId` instead of bare `String`.

## Steps
- [x] Read all relevant files (Wingman.kt, ClaudeCodeWingman.kt, HandshakeGuid.kt, ClaudeCodeWingmanTest.kt)
- [x] Create `AgentType.kt` enum (CLAUDE_CODE, PI)
- [x] Create `ResumableAgentSessionId.kt` data class
- [x] Update `Wingman.kt` return type + doc comment
- [x] Update `ClaudeCodeWingman.kt` return type + return value
- [x] Update `ClaudeCodeWingmanTest.kt` assertions
- [x] Run tests and verify green

## Files to touch
- NEW: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/AgentType.kt`
- NEW: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ResumableAgentSessionId.kt`
- MODIFY: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt`
- MODIFY: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`
- MODIFY: `app/src/test/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingmanTest.kt`

## Status
COMPLETE — all 12 wingman tests pass, BUILD SUCCESSFUL
