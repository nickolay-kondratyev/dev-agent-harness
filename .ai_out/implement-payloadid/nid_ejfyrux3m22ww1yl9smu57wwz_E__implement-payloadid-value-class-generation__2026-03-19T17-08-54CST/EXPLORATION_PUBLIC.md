# Exploration: PayloadId Implementation

## Key Findings

- `HandshakeGuid` exists at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt`
  - Has `handshake.` prefix, `value` property, `toString()` override
  - Uses `@JvmInline value class` pattern
- Target directory `app/src/main/kotlin/com/glassthought/shepherd/core/server/` does not exist yet — needs creation
- Test pattern: `AsgardDescribeSpec` with BDD GIVEN/WHEN/THEN, one assert per `it` block
- No existing tests for HandshakeGuid to reference

## Implementation Scope
- Create `PayloadId.kt` value class with `generate(handshakeGuid, counter)` factory
- Create `PayloadIdTest.kt` with 4 unit tests per ticket spec
