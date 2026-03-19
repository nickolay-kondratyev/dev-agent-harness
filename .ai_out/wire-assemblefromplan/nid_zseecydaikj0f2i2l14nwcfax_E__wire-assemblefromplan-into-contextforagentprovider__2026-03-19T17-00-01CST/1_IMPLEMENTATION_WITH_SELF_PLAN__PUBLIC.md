# Wire assembleFromPlan into ContextForAgentProviderImpl

## What Was Done

Replaced the procedural `build*Sections()` pattern in `ContextForAgentProviderImpl` with data-driven
instruction plans delegated to `InstructionPlanAssembler.assembleFromPlan()`.

### Changes

1. **Rewrote `ContextForAgentProviderImpl`** — replaced 4 `build*Sections()` methods and ~15 helper
   methods with 4 `build*Plan()` methods that return `List<InstructionSection>`. The `when` dispatch
   builds a plan and delegates to the injected `InstructionPlanAssembler`.

2. **Injected `InstructionPlanAssembler`** via constructor. Updated `ContextForAgentProvider.standard()`
   factory to create and pass the assembler.

3. **Inlined `InstructionRenderers` into `InstructionSection` subtypes** — moved rendering logic from
   `partContext()`, `callbackScriptUsage()`, `feedbackItemInstructions()`, and `roleCatalog()` directly
   into their respective `InstructionSection.render()` methods. Deleted `InstructionRenderers.kt`.

4. **Added `InlineStringContentSection`** — new `InstructionSection` subtype for string content (not
   file-backed). Used by PlanReviewer for `planJsonContent` and `planMdContent` which arrive as strings.
   Supports optional `codeBlockLanguage` parameter for fenced code blocks.

5. **Enhanced `FeedbackDirectorySection`** — added `headerBody` parameter to preserve the descriptive
   paragraph text from `InstructionText.ADDRESSED_FEEDBACK_HEADER` etc. that was previously concatenated
   in the old code.

6. **Updated `PriorPublicMd`** rendering to include `# Prior Agent Outputs` heading with numbered
   sub-headings, matching the old provider behavior.

7. **Added section ordering tests** — `InstructionSectionOrderingTest` verifies that all four roles
   (doer, reviewer, planner, plan reviewer) produce sections in the spec-defined order.

8. **Added `InlineStringContentSection` unit tests** — both plain content and json code block variants.

## Decisions

- **PriorPublicMd heading format**: Changed from `## filename` (original InstructionSection format) to
  `## Prior Output N: filename` wrapped in `# Prior Agent Outputs`. This matches the old provider output
  and satisfies existing assembly tests.

- **FeedbackDirectorySection headerBody**: Rather than losing the descriptive paragraphs from
  `InstructionText.*_HEADER` constants, added an optional `headerBody` parameter that renders the full
  InstructionText block when present. Falls back to simple `## heading` when absent (backward compatible
  with existing tests).

- **Heading levels for PlanReviewer inline content**: Changed from h1 (`# plan_flow.json`) to h2
  (`## plan_flow.json`) for consistency with `InlineFileContentSection` pattern. No tests depended on
  heading level.

## Files Modified
- `app/src/main/kotlin/.../context/ContextForAgentProviderImpl.kt` — full rewrite
- `app/src/main/kotlin/.../context/ContextForAgentProvider.kt` — factory + KDoc update
- `app/src/main/kotlin/.../context/InstructionSection.kt` — inlined renderers, added InlineStringContentSection, updated PriorPublicMd
- `app/src/main/kotlin/.../context/ProtocolVocabulary.kt` — KDoc reference update
- `app/src/main/kotlin/.../context/InstructionRenderers.kt` — DELETED
- `app/src/test/.../context/InstructionSectionTest.kt` — updated PriorPublicMd headings, added InlineStringContentSection tests
- `app/src/test/.../context/InstructionSectionOrderingTest.kt` — NEW, section ordering verification
