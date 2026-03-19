# Feedback-Loop InstructionSection Subtypes — Implementation Summary

## What Was Done

Added 4 new sealed subtypes to `InstructionSection` (ap.YkR8mNv3pLwQ2xJtF5dZs.E) enabling plan-driven assembly of feedback-loop sections via `InstructionPlanAssembler`:

1. **FeedbackItem** — data class wrapping `InstructionRenderers.feedbackItemInstructions()` for doer inner-loop per-feedback-item processing. Always non-null.
2. **StructuredFeedbackFormat** — data object returning `InstructionText.REVIEWER_FEEDBACK_FORMAT`. Reviewer-only.
3. **FeedbackWritingInstructions** — data object returning `InstructionText.FEEDBACK_WRITING_INSTRUCTIONS`. Reviewer-only.
4. **FeedbackDirectorySection** — data class that globs `*.md` files in a directory, optionally filtered by `filenamePrefix`, renders each under `### filename` sub-headings, joined with `---` separators under a `## heading`. Returns `null` for absent/empty directories.

## Files Modified

- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` — 4 new sealed subtypes added (sections 8-11)
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` — BDD tests for all 4 subtypes
- `doc/core/ContextForAgentProvider.md` — updated FeedbackDirectorySection entry to reflect `filenamePrefix` parameter

## Design Decisions

- `FeedbackDirectorySection` duplicates the glob logic from `ContextForAgentProviderImpl.collectMarkdownFilesInDir()` rather than extracting a shared utility — keeps InstructionSection self-contained and avoids coupling to ContextForAgentProviderImpl's private methods.
- All existing subtypes and tests are untouched.

## Tests

All tests pass: BUILD SUCCESSFUL (7 actionable tasks).
