# Implementation Iteration - Private State

## What was done

- Created `ShepherdValType` object at `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt`
  with three `ValTypeV2` entries: `PAYLOAD_ID`, `ATTEMPT_NUMBER`, `MAX_ATTEMPTS`
- Updated `AckedPayloadSenderImpl` to import `ShepherdValType` instead of `ValType` and use specific types
- Removed unused `ValType` import from `AckedPayloadSender.kt`

## Design Decisions

- `ShepherdValType` placed in `com.glassthought.shepherd.core` package (not in `server` subpackage)
  because it is a project-wide cross-cutting concern that will be reused by other classes
- Used `object` (static class) per CLAUDE.md: "for stateless utilities, use a static class"
- All types use `UserSpecificity.USER_AGNOSTIC` since payload IDs, attempt numbers, and max attempts
  contain no user-specific data
- All types use `String::class` as `expectedValClass` since the values are passed as `.toString()` strings

## Tests

All existing tests pass without modification — the `Val` type changes are backward-compatible
since `Val` accepts both `ValType` and `ValTypeV2`.

## Next Steps

- Other files in the codebase also use generic `ValType.STRING_USER_AGNOSTIC` (e.g., `AgentUnresponsiveUseCase`,
  `ClaudeCodeAdapter`). These should be updated to use `ShepherdValType` entries in follow-up work.
- The `SessionEntry` data class semantics question from the review remains as a follow-up evaluation.
