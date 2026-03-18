---
id: nid_r2rdkc0t9jd9597sumbgzp7aw_E
title: "Implement execution + planner InstructionSection subtypes"
status: open
deps: [nid_7vpbal1qdmrvt23g44vpq6hgv_E]
links: [nid_zseecydaikj0f2i2l14nwcfax_E, nid_7vpbal1qdmrvt23g44vpq6hgv_E, nid_gp9rduvxoqf14m95z9bttnaxq_E]
created_iso: 2026-03-18T18:17:05Z
status_updated_iso: 2026-03-18T18:17:05Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ContextForAgentProvider, InstructionSection]
---

## Goal

Implement the role-specific InstructionSection subtypes for execution agents (doer/reviewer) and planner agents, as specified in `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E).

Depends on the InstructionSection engine being in place (nid_7vpbal1qdmrvt23g44vpq6hgv_E).

## What to Build

### Execution-specific sections

| Section type | Description | Used by |
|---|---|---|
| `PlanMd` | `shared/plan/PLAN.md` — included for `with-planning` workflows only, absent for straightforward | Doer, Reviewer |
| `PriorPublicMd` | Prior completed PUBLIC.md files per visibility rules | Doer, Reviewer |
| `IterationFeedback` | Reviewer PUBLIC.md + pushback guidance — doer only, iteration > 1 | Doer |
| `InlineFileContentSection(heading, path)` | Generic: read file, render under heading. Used for doer output in reviewer, planner PUBLIC.md in plan-reviewer, plan content in plan-reviewer, prior feedback, etc. | Multiple roles |

**Note:** `FeedbackItem`, `StructuredFeedbackFormat`, `FeedbackWritingInstructions`, and `FeedbackDirectorySection` are NOT in this ticket — they belong to the feedback-loop ticket (`nid_gp9rduvxoqf14m95z9bttnaxq_E`).

### Planner-specific sections

| Section type | Description | Used by |
|---|---|---|
| `RoleCatalog` | All role definitions (name + description + description_long) | Planner |
| `AvailableAgentTypes` | Supported agent types + models — V1: ClaudeCode only, models: opus/sonnet | Planner, PlanReviewer |
| `PlanFormatInstructions` | JSON schema for `plan_flow.json` | Planner |

## Visibility Rules (for PriorPublicMd)

For a sub-part in part N:
- All sub-parts PUBLIC.md from parts 1 through N-1 (completed parts)
- Peer sub-part PUBLIC.md within the same part (if exists)
- NOT future parts, NOT planning phase PUBLIC.md files

## Files

- Edit: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` — add new subtypes
- New tests in `app/src/test/kotlin/com/glassthought/shepherd/core/context/`
- Reference existing: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionRenderers.kt`
- Reference existing: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionText.kt`

## Key Design

- `InlineFileContentSection(heading: String, path: Path?)` is a generic section — read file at path, render content under the heading. Replaces 6+ specific section types from the spec that were structurally identical.
  - **When path is non-null**: fail hard if file does not exist (upstream guarantees existence — e.g., PUBLIC.md validated before assembly, plan_flow.json written by planner)
  - **When path is null**: skip silently (used for conditional sections like planner's reviewer feedback on iteration 1, where `planReviewerPublicMdPath: Path?` is null)
- `PriorPublicMd` receives the list of paths from the request (`ExecutionContext.priorPublicMdPaths`) and renders each as a labeled section
- `AvailableAgentTypes` is static text for V1 — hardcoded ClaudeCode + opus/sonnet options
- `IterationFeedback` includes the pushback guidance text from `doc/core/ContextForAgentProvider.md` "Doer Pushback Guidance" section, wrapped in `<critical_to_keep_through_compaction>` tags

## Spec Reference
- `doc/core/ContextForAgentProvider.md` — Doer/Reviewer/Planner/PlanReviewer section tables + Visibility Rules

## Acceptance Criteria

1. All 7 section subtypes implemented with renderers
2. Unit tests for each section type
3. InlineFileContentSection: with non-null path + file present → renders content; with non-null path + file missing → fails hard; with null path → silently skips
4. PriorPublicMd renders correct paths per visibility rules
5. AvailableAgentTypes includes V1 constraints
6. IterationFeedback wraps pushback guidance in compaction-survival tags
7. All tests pass via `./test.sh`


## Notes

**2026-03-18T18:21:29Z**

Merge note: This ticket and nid_gp9rduvxoqf14m95z9bttnaxq_E both add subtypes to InstructionSection.kt. If run in parallel, expect a merge conflict on that file. The conflict will be trivial (additive subtypes), but the implementing agent should be aware.

**2026-03-18T18:32:17Z**

**IterationFeedback null-path behavior (from review):** Add AC criterion: IterationFeedback with `reviewerPublicMdPath == null` (iteration 1) silently produces no output. This is distinct from InlineFileContentSection with non-null path that fails hard if file missing.
