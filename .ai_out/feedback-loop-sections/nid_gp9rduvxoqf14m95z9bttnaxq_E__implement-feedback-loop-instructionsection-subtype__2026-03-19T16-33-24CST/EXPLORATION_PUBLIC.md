# Exploration Findings

## Architecture Summary

### InstructionSection (sealed class, ap.YkR8mNv3pLwQ2xJtF5dZs.E)
- 7 existing subtypes: RoleDefinition, PrivateMd, PartContext, Ticket, OutputPathSection, WritingGuidelines, CallbackHelp
- `abstract fun render(request: AgentInstructionRequest): String?` — null = skip
- InstructionPlanAssembler (ap.Xk7mPvR3nLwQ9tJsF2dYh.E) walks plan list, renders, filters nulls, joins with `---`

### Existing Support Infrastructure (all already implemented)
- `InstructionRenderers.feedbackItemInstructions(feedbackContent, currentPath, isOptional)` — returns full feedback item text
- `InstructionText.REVIEWER_FEEDBACK_FORMAT` — structured feedback format with compaction tags
- `InstructionText.FEEDBACK_WRITING_INSTRUCTIONS` — feedback file writing instructions
- `InstructionText.ADDRESSED_FEEDBACK_HEADER`, `REJECTED_FEEDBACK_HEADER`, `SKIPPED_OPTIONAL_HEADER`
- `ProtocolVocabulary.FeedbackStatus`, `Severity`, `SeverityPrefix` constants
- `ContextForAgentProviderImpl.collectMarkdownFilesInDir()` — existing glob pattern for feedback dirs

### Key Observation
`ContextForAgentProviderImpl` already has private methods doing the feedback directory globbing and the reviewer sections inline. The ticket asks us to create InstructionSection subtypes that **wrap** this existing behavior so it can be used in plan-driven assembly (InstructionPlanAssembler).

### Two Assembly Paths
1. **Old path**: `ContextForAgentProviderImpl.buildDoerSections()` / `buildReviewerSections()` — uses private methods directly
2. **New path**: `InstructionPlanAssembler.assembleFromPlan(plan, request)` — uses InstructionSection subtypes

The new subtypes enable the plan-driven path to handle feedback loop sections.

## Files to Edit
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` — add 4 new subtypes
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` — add tests
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt` — may need new fixture helpers
- `doc/core/ContextForAgentProvider.md` — update per ticket notes

## 4 New Section Types

### 1. FeedbackItem (data class)
- Params: feedbackContent: String, currentPath: Path, isOptional: Boolean
- Delegates to `InstructionRenderers.feedbackItemInstructions()`
- Used by Doer in inner feedback loop
- Always returns non-null String

### 2. StructuredFeedbackFormat (data object)
- Returns `InstructionText.REVIEWER_FEEDBACK_FORMAT`
- Wrapped in compaction-survival tags (already in the constant)
- Used by Reviewer

### 3. FeedbackWritingInstructions (data object)
- Returns `InstructionText.FEEDBACK_WRITING_INSTRUCTIONS`
- No compaction tags needed
- Used by Reviewer

### 4. FeedbackDirectorySection (data class)
- Params: dir: Path, heading: String
- Globs `dir/*.md`, renders each file as `### filename\n\ncontent`
- Empty directory → returns null (skip)
- Reuses pattern from `collectMarkdownFilesInDir()`
- Used for addressed/, rejected/, pending/optional__* directories
- Optional filenamePrefix param needed for the pending/optional filter case
