# Align Feedback Structure with 3-Directory Model

## What was done

Aligned InstructionText, ProtocolVocabulary, InstructionRenderers, ContextForAgentProviderImpl, and test fixtures
with the new 3-directory feedback structure specified in `doc/plan/granular-feedback-loop.md`
(ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

### Changes by file

**ProtocolVocabulary.kt:**
- `FeedbackStatus.UNADDRESSED` renamed to `FeedbackStatus.PENDING` (value `"pending"`)
- Added `FeedbackStatus.SKIPPED = "skipped"` for optional items doer chooses not to address
- Added `SeverityPrefix` object with `CRITICAL = "critical__"`, `IMPORTANT = "important__"`, `OPTIONAL = "optional__"`
- Deleted `MOVEMENT_LOG` constant entirely (agents no longer write movement records)

**InstructionText.kt:**
- `FEEDBACK_WRITING_INSTRUCTIONS`: Rewritten for `__feedback/pending/` with severity filename prefixes. Movement Log template replaced with `## Resolution:` marker instructions.
- `REJECTED_FEEDBACK_HEADER`: "move file back to unaddressed/" changed to "re-file to pending/ with new severity"
- `SKIPPED_OPTIONAL_HEADER`: References updated from `unaddressed/optional/` to `pending/` with `optional__` prefix pattern

**InstructionRenderers.kt:**
- `feedbackItemInstructions()`: Removed `addressedPath` and `rejectedPath` parameters. Replaced file-movement instructions with `## Resolution: ADDRESSED/REJECTED/SKIPPED` marker approach. Removed Movement Log format section entirely.

**ContextForAgentProviderImpl.kt:**
- `feedbackStateSections()`: Now reads flat `addressed/` and `rejected/` directories. Skipped-optional reads `pending/` filtered by `optional__*` prefix.
- `collectFeedbackFiles()` replaced with `collectFeedbackFilesInFlatDir()` that reads a single flat directory.
- `collectMarkdownFilesInDir()` gained optional `filenamePrefix` parameter for filtering.

**Test Fixtures:**
- Old: `unaddressed/critical/`, `addressed/critical/`, `rejected/important/` (nested severity subdirs)
- New: `pending/`, `addressed/`, `rejected/` (flat dirs with severity-prefixed filenames)
- `## Movement Log` sections replaced with `## Resolution:` markers in all fixture files

**ExecutionAgentInstructionsKeywordTest.kt:**
- `UNADDRESSED` assertion changed to `PENDING`
- `MOVEMENT_LOG` assertion replaced with `SeverityPrefix.CRITICAL` assertion

## Decisions

- `ContextForAgentProviderAssemblyTest.kt` required no changes — all assertions still pass because the fixture restructuring is transparent to the assembly logic (same content, different paths).
- The `feedbackItemInstructions()` signature change is safe since there are zero callers (confirmed by grep).

## Tests

All tests pass: `./gradlew :app:test` - BUILD SUCCESSFUL (6s, 6 tasks).
