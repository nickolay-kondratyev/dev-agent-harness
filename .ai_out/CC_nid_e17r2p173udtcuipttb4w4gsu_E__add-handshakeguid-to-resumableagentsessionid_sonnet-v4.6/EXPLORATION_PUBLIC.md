# Exploration: Add HandshakeGuid to ResumableAgentSessionId

## Current State

`ResumableAgentSessionId` has 3 fields: `agentType`, `sessionId`, `model`.

`HandshakeGuid` is a `@JvmInline value class` in the same package.

## Files to Modify (3 total)

### 1. ResumableAgentSessionId.kt
Add `handshakeGuid: HandshakeGuid` as the first field.

### 2. ClaudeCodeAgentSessionIdResolver.kt (line 130)
Pass `guid` (already available as `resolveSessionId(guid: HandshakeGuid)` parameter) when constructing the result:
```kotlin
ResumableAgentSessionId(guid, AgentType.CLAUDE_CODE, sessionId, model)
```

### 3. ClaudeCodeAgentSessionIdResolverTest.kt (3 assertions)
- Line 41: uses `guid = HandshakeGuid("test-guid-abc123-unique-marker")`
- Line 158: uses same outer `guid`
- Line 212: uses inner `guid = HandshakeGuid("polling-test-guid")`

## No Other Callers Found
`ResumableAgentSessionId(` constructor only exists in the 4 locations above (1 production + 3 tests).
