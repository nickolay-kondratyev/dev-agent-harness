# Implementation Private State

## Status: COMPLETE

## What Was Done
- Created `AgentTypeAdapter` interface (ref.ap.hhP3gT9qK2mR8vNwX5dYa.E) in `adapter` package
- Created `ClaudeCodeAdapter` merging starter + resolver (ref.ap.gCgRdmWd9eTGXPbHJvyxI.E)
- Extracted `GuidScanner` to own file (detekt `MatchingDeclarationName`)
- Updated `ContextInitializer` to wire `ClaudeCodeAdapter`
- Migrated all tests to `ClaudeCodeAdapterTest`
- Deleted old files + empty dirs
- Updated doc references in HandshakeGuid, ResumableAgentSessionId, Constants
- Regenerated detekt baseline
- All tests pass

## Issues Encountered & Resolved
1. **Nested `/*` in KDoc**: `/*.jsonl` glob pattern in KDoc created a nested comment that Kotlin's parser couldn't close. Fixed by removing the glob pattern from the KDoc.
2. **`$` in Kotlin strings**: Shell escaping function used `"$"` which Kotlin interprets as string interpolation. Fixed with a `const val DOLLAR = '$'` companion constant.
3. **Detekt issues**: `MayBeConst`, `UseCheckOrError`, `MatchingDeclarationName`, `UnusedPrivateClass` — all fixed.

## Anchor Points
- `ap.hhP3gT9qK2mR8vNwX5dYa.E` — AgentTypeAdapter interface
- `ap.gCgRdmWd9eTGXPbHJvyxI.E` — ClaudeCodeAdapter (preserved from old resolver)
