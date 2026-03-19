# Implementation Private State

## Status: COMPLETE (post-review iteration done)

## What Was Done (original)
- Created `AgentTypeAdapter` interface (ref.ap.hhP3gT9qK2mR8vNwX5dYa.E) in `adapter` package
- Created `ClaudeCodeAdapter` merging starter + resolver (ref.ap.gCgRdmWd9eTGXPbHJvyxI.E)
- Extracted `GuidScanner` to own file (detekt `MatchingDeclarationName`)
- Updated `ContextInitializer` to wire `ClaudeCodeAdapter`
- Migrated all tests to `ClaudeCodeAdapterTest`
- Deleted old files + empty dirs
- Updated doc references in HandshakeGuid, ResumableAgentSessionId, Constants
- Regenerated detekt baseline
- All tests pass

## Review Iteration (2nd pass)
- Updated spec at ref.ap.A0L92SUzkG3gE0gX04ZnK.E to show `BuildStartCommandParams` instead of `String`
- Refactored ClaudeCodeAdapter to factory pattern:
  - Primary constructor is `internal`, takes `GuidScanner` directly
  - `companion fun create(...)` is the production factory
  - Eliminated mutable `var guidScanner`, `PLACEHOLDER_PATH`, throwaway object
- Updated production wiring (`ContextInitializer`) and all tests
- All tests pass

## Issues Encountered & Resolved
1. **Nested `/*` in KDoc**: `/*.jsonl` glob pattern in KDoc created a nested comment that Kotlin's parser couldn't close. Fixed by removing the glob pattern from the KDoc.
2. **`$` in Kotlin strings**: Shell escaping function used `"$"` which Kotlin interprets as string interpolation. Fixed with a `const val DOLLAR = '$'` companion constant.
3. **Detekt issues**: `MayBeConst`, `UseCheckOrError`, `MatchingDeclarationName`, `UnusedPrivateClass` — all fixed.
4. **Review: spec divergence**: Updated spec to match implementation's `BuildStartCommandParams`.
5. **Review: public test constructor**: Refactored to factory pattern, making primary constructor `internal`.

## Anchor Points
- `ap.hhP3gT9qK2mR8vNwX5dYa.E` — AgentTypeAdapter interface
- `ap.gCgRdmWd9eTGXPbHJvyxI.E` — ClaudeCodeAdapter (preserved from old resolver)
