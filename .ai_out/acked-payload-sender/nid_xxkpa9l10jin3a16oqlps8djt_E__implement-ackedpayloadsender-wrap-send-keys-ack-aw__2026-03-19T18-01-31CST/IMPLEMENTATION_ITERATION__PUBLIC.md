# AckedPayloadSender Implementation Iteration

## Review Feedback Addressed

### Fixed: ValType specificity (IMPORTANT #2 from review)

**Problem**: All `Val(...)` usages in `AckedPayloadSenderImpl` used generic `ValType.STRING_USER_AGNOSTIC`,
which reduces the value of structured logging for filtering and analysis.

**Solution**: Created `ShepherdValType` object with project-specific `ValTypeV2` definitions:

- `ShepherdValType.PAYLOAD_ID` — for payload identifiers (handshakeGuid + counter)
- `ShepherdValType.ATTEMPT_NUMBER` — for current attempt number in retry loops
- `ShepherdValType.MAX_ATTEMPTS` — for maximum attempt count in retry policies

Updated all 6 `Val(...)` usages in `AckedPayloadSenderImpl` to use the specific types.

### Not Fixed (follow-up ticket scope): `data class SessionEntry` semantics

Per plan, the `data class SessionEntry` question (IMPORTANT #1) is not addressed here.
It is a valid concern but requires separate evaluation.

## Files Changed

- **New**: `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt`
  - Project-specific `ValTypeV2` definitions for TICKET_SHEPHERD structured logging
- **Modified**: `app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt`
  - Replaced `ValType.STRING_USER_AGNOSTIC` with `ShepherdValType.PAYLOAD_ID`, `ShepherdValType.ATTEMPT_NUMBER`, `ShepherdValType.MAX_ATTEMPTS`

## Test Results

All tests pass (`./gradlew :app:test` exit code 0).
