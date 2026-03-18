---
id: nid_gp9rduvxoqf14m95z9bttnaxq_E
title: "Implement feedback-loop InstructionSection subtypes"
status: open
deps: [nid_7vpbal1qdmrvt23g44vpq6hgv_E]
links: [nid_zseecydaikj0f2i2l14nwcfax_E, nid_7vpbal1qdmrvt23g44vpq6hgv_E, nid_r2rdkc0t9jd9597sumbgzp7aw_E]
created_iso: 2026-03-18T18:17:35Z
status_updated_iso: 2026-03-18T18:17:35Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ContextForAgentProvider, InstructionSection, feedback-loop]
---

## Goal

Implement the feedback-loop-specific InstructionSection subtypes for the granular per-item feedback loop, as specified in `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) and `doc/plan/granular-feedback-loop.md` (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

Depends on the InstructionSection engine being in place (nid_7vpbal1qdmrvt23g44vpq6hgv_E).

## What to Build

| Section type | Description | Used by |
|---|---|---|
| `FeedbackItem` | Single feedback file content + resolution marker instructions (ADDRESSED/REJECTED/SKIPPED) + feedback file path. Used per-item in the inner feedback loop. | Doer (inner loop) |
| `StructuredFeedbackFormat` | Static text instructing reviewer to follow structured feedback format (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) | Reviewer |
| `FeedbackWritingInstructions` | Static text on how to write feedback files to `__feedback/pending/` with severity filename prefixes (`critical__`, `important__`, `optional__`) | Reviewer |
| `FeedbackDirectorySection(dir, heading)` | Glob a feedback directory and render all files under a heading. Used for addressed, rejected, and remaining optional feedback on reviewer iteration > 1. | Reviewer (iteration > 1) |

## Key Design Details

### FeedbackItem
- Contains: the feedback file content, a labeled path to the file, resolution marker instructions
- Resolution markers: `## Resolution: ADDRESSED`, `## Resolution: REJECTED`, `## Resolution: SKIPPED` (optional items only)
- SKIPPED: valid only for `optional__` prefixed files — doer reviewed and chose not to act
- The harness reads the marker and moves the file to `addressed/` or `rejected/`

### FeedbackDirectorySection
- Globs `__feedback/${subdir}/*.md` (e.g., `__feedback/addressed/*.md`)
- Renders each file content under the heading
- Empty directory → renders nothing (skip silently)
- Used for 3 dirs: `addressed/`, `rejected/`, `pending/optional__*`

### StructuredFeedbackFormat
- The structured format from spec section "Required PUBLIC.md Format on needs_iteration"
- Includes: `## Verdict`, `## Issues` (with severity), `## Acceptance Criteria`, `## What Passed`
- Wrapped in `<critical_to_keep_through_compaction>` tags

### FeedbackWritingInstructions
- Instructions for writing to `__feedback/pending/` with severity prefixes
- Includes filename format: `{severity}__{descriptive_name}.md`
- Severity levels: `critical__`, `important__`, `optional__`

## Spec References
- `doc/core/ContextForAgentProvider.md` — Reviewer table rows 6a-6e, Doer table row 7a
- `doc/plan/granular-feedback-loop.md` (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) — full feedback loop spec
- `doc/core/ContextForAgentProvider.md` "Structured Reviewer Feedback Contract" (ref.ap.EslyJMFQq8BBrFXCzYw5P.E)

## Files

- Edit: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` — add new subtypes
- New tests in `app/src/test/kotlin/com/glassthought/shepherd/core/context/`

## Acceptance Criteria

1. All 4 feedback-loop section subtypes implemented with renderers
2. FeedbackItem includes resolution marker instructions with ADDRESSED/REJECTED/SKIPPED
3. FeedbackItem SKIPPED only valid for `optional__` prefix — documented in rendered instructions
4. FeedbackDirectorySection correctly globs files and renders under heading
5. FeedbackDirectorySection produces no output for empty directories
6. StructuredFeedbackFormat includes the spec-defined format in compaction-survival tags
7. Unit tests for each section type
8. All tests pass via `./test.sh`


## Notes

**2026-03-18T18:21:29Z**

Merge note: This ticket and nid_r2rdkc0t9jd9597sumbgzp7aw_E both add subtypes to InstructionSection.kt. If run in parallel, expect a merge conflict on that file. The conflict will be trivial (additive subtypes), but the implementing agent should be aware.
