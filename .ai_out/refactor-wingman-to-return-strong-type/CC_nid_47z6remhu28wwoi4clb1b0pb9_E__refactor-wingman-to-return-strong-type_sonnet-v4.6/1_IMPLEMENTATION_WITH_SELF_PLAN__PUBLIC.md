# Implementation: Refactor Wingman to Return Strong Type

## Status: COMPLETE

## What Was Done

Refactored `Wingman.resolveSessionId` to return the strongly-typed `ResumableAgentSessionId`
data class instead of a bare `String`.

### New Files Created

1. `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/AgentType.kt`
   - Enum with `CLAUDE_CODE` and `PI` variants.

2. `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ResumableAgentSessionId.kt`
   - `data class ResumableAgentSessionId(val agentType: AgentType, val sessionId: String)`
   - References the `Wingman` interface via anchor point.

### Files Modified

3. `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt`
   - Changed `resolveSessionId` return type from `String` to `ResumableAgentSessionId`.
   - Updated `@return` doc comment accordingly.

4. `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`
   - Changed `resolveSessionId` override return type to `ResumableAgentSessionId`.
   - Returns `ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)` instead of bare `sessionId`.

5. `app/src/test/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingmanTest.kt`
   - Updated 3 assertions from bare `String` to `ResumableAgentSessionId`:
     - `result shouldBe sessionId` → `result shouldBe ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)` (2 occurrences)
     - `result shouldBe "abc-session-id-123"` → `result shouldBe ResumableAgentSessionId(AgentType.CLAUDE_CODE, "abc-session-id-123")`

## Test Results

All 12 `ClaudeCodeWingmanTest` tests passed (0 failures, 0 errors).
Full build: `BUILD SUCCESSFUL`.

## Notes

- No production callers of `resolveSessionId` existed at the time of refactor (only tests).
- No architectural decisions beyond what the ticket specified.
- The test file is in the same package as production code, so no import statements were needed
  for `ResumableAgentSessionId` and `AgentType` in the test file.
