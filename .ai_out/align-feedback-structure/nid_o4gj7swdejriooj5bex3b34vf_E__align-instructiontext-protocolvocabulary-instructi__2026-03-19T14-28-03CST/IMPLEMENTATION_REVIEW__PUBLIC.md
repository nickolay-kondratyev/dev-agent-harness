# Implementation Review: Align Feedback Structure with 3-Directory Model

## Summary

This change aligns `ProtocolVocabulary`, `InstructionText`, `InstructionRenderers`,
`ContextForAgentProviderImpl`, test fixtures, and keyword tests with the new 3-directory
feedback structure specified in `doc/plan/granular-feedback-loop.md`
(ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

**Key changes:**
- `FeedbackStatus.UNADDRESSED` renamed to `PENDING`; `SKIPPED` status added
- `SeverityPrefix` object added with `critical__`, `important__`, `optional__` constants
- `MOVEMENT_LOG` constant deleted entirely
- Instruction text updated: 9-dir paths replaced with flat `pending/` + severity filename prefixes;
  Movement Log template replaced with `## Resolution:` marker instructions
- `feedbackItemInstructions()` simplified: removed `addressedPath`/`rejectedPath` parameters,
  replaced file-movement instructions with resolution marker approach
- `ContextForAgentProviderImpl`: `collectFeedbackFiles()` (which traversed `{status}/{severity}/`)
  replaced with `collectFeedbackFilesInFlatDir()` reading flat directories; `collectMarkdownFilesInDir()`
  gained optional `filenamePrefix` parameter
- Test fixtures restructured from nested severity subdirectories to flat dirs with severity-prefixed filenames
- Keyword test updated: `UNADDRESSED` assertion replaced with `PENDING`; `MOVEMENT_LOG` assertion
  replaced with `SeverityPrefix.CRITICAL`

**Overall assessment: PASS.** The implementation is correct, complete, and well-aligned with the spec.
All tests pass. No remaining references to old patterns in source code. A few minor suggestions below.

---

## No CRITICAL Issues

None found.

---

## No IMPORTANT Issues

None found.

---

## Suggestions

### 1. Missing keyword test for `FeedbackStatus.SKIPPED`

`SKIPPED` is a new protocol keyword added to `ProtocolVocabulary.FeedbackStatus` and used in both
`InstructionText.FEEDBACK_WRITING_INSTRUCTIONS` and `InstructionRenderers.feedbackItemInstructions()`.
The reviewer keyword test (`ExecutionAgentInstructionsKeywordTest.kt`) verifies `PENDING`,
`ADDRESSED`, and `REJECTED` but does not assert `SKIPPED` is present in the assembled reviewer
instructions.

The keyword test's purpose (per the class KDoc) is to catch protocol keywords that are removed
from instruction text. Adding a `shouldContain ProtocolVocabulary.FeedbackStatus.SKIPPED` assertion
for the reviewer instructions would complete coverage.

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/test/kotlin/com/glassthought/shepherd/core/context/ExecutionAgentInstructionsKeywordTest.kt`

Suggested addition after line 121 (the `REJECTED` assertion):

```kotlin
it("THEN contains 'skipped' feedback status") {
    text shouldContain ProtocolVocabulary.FeedbackStatus.SKIPPED
}
```

### 2. Missing keyword test for `SeverityPrefix.IMPORTANT` and `SeverityPrefix.OPTIONAL`

The test asserts `SeverityPrefix.CRITICAL` is present (line 139) but not the other two prefixes.
Since all three are distinct protocol vocabulary used in `FEEDBACK_WRITING_INSTRUCTIONS`, testing
all three would be consistent and catch any accidental removal.

### 3. Old references in tickets and change log files

The grep for `UNADDRESSED|MOVEMENT_LOG` found references in:
- `_tickets/align-instructiontext-protocolvocabulary-instructionrenderers-with-new-3-directory-feedback-structure.md`
- `_tickets/implement-aioutputstructure-path-resolution-for-aiout-directory-schema.md`
- `_change_log/2026-03-18_22-08-53Z.md`

These are documentation/tracking files (not source code) and reference the old structure as historical
context or task description, so they are **not broken** -- just worth noting. No action needed.

### 4. Consider testing the `filenamePrefix` filtering path explicitly

`collectMarkdownFilesInDir()` gained a `filenamePrefix` parameter, and the skipped-optional
section in `feedbackStateSections()` relies on it. The current test fixtures don't have any
`optional__*` files in `pending/` (the only file there is `critical__missing-null-check.md`),
so the filtering logic is not exercised by existing tests. A fixture file like
`pending/optional__minor-style-issue.md` with a `## Resolution: SKIPPED` marker would exercise
the prefix filtering path through `ContextForAgentProviderAssemblyTest`.

---

## Documentation Updates Needed

None. The code changes are self-documenting through the spec references, and the spec itself
(`doc/plan/granular-feedback-loop.md`) already describes the 3-directory structure.
