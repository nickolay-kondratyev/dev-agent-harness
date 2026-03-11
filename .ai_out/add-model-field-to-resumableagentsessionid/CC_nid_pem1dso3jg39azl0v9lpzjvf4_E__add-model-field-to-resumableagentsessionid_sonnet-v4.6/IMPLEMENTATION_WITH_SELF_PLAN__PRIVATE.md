# IMPLEMENTATION_WITH_SELF_PLAN - PRIVATE

## Task
Add `model: String` field to `ResumableAgentSessionId`, inject via constructor into `ClaudeCodeAgentSessionIdResolver`.

## Plan

**Goal**: Add `val model: String` as 3rd field to `ResumableAgentSessionId`, thread it through from `ClaudeCodeAgentStarterBundleFactory`.

**Steps**:
- [x] 1. Add `val model: String` to `ResumableAgentSessionId` (after `sessionId`)
- [x] 2. Add `model: String` to both constructors in `ClaudeCodeAgentSessionIdResolver`; use in `ResumableAgentSessionId` creation
- [x] 3. Refactor `ClaudeCodeAgentStarterBundleFactory.create()` to extract model variable, pass to resolver
- [x] 4. Update `ClaudeCodeAgentSessionIdResolverTest.kt` - all resolver instantiations and assertions
- [x] 5. Update `SpawnTmuxAgentSessionUseCaseIntegTest.kt` - add model assertion
- [x] 6. Run tests - BUILD SUCCESSFUL

## Files
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt`
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt`
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/impl/ClaudeCodeAgentStarterBundleFactory.kt`
- `app/src/test/kotlin/com/glassthought/ticketShepherd/core/sessionresolver/impl/ClaudeCodeAgentSessionIdResolverTest.kt`
- `app/src/test/kotlin/com/glassthought/bucket/SpawnTmuxAgentSessionUseCaseIntegTest.kt`

## Status
COMPLETE - All tests pass.

## Notes
- Initial replace_all edit missed 2 test instances (no "Short timeout" comment context) — fixed individually.
- `model` parameter placed between `outFactory` and `resolveTimeoutMs` in constructor to keep timeout params at the end (consistent with existing pattern).
