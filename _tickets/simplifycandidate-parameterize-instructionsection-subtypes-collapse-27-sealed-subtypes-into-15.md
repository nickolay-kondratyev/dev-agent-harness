---
closed_iso: 2026-03-18T14:36:52Z
id: nid_2njb0z4sew9d9hmr8hq8a0zlo_E
title: "SIMPLIFY_CANDIDATE: Parameterize InstructionSection subtypes — collapse 27 sealed subtypes into ~15"
status: closed
deps: []
links: []
created_iso: 2026-03-18T14:24:42Z
status_updated_iso: 2026-03-18T14:36:52Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, dry]
---

## Problem

`InstructionSection` sealed class has 27 distinct subtypes. Many are thin wrappers differing only in a path or heading value:
- `PlanFlowJsonOutputPath` / `PlanMdOutputPath` / `PublicMdOutputPath` — all "output path with label"
- `PlanFlowJsonContent` / `PlanMdContent` — both "inline file content with heading"
- `AddressedFeedback` / `RejectedFeedback` / `RemainingOptionalFeedback` — all "glob feedback directory with heading"

## Spec Reference

- `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E)

## Proposed Change

Introduce parameterized subtypes:
- `OutputPathSection(label: String, path: Path)` replaces 3 subtypes
- `InlineFileContentSection(heading: String, path: Path)` replaces 2 subtypes
- `FeedbackDirectorySection(dir: Path, heading: String)` replaces 3 subtypes

This brings the count from ~27 to ~19, with potential for further collapse.

## Justification

- **DRY**: Multiple subtypes with identical rendering logic = knowledge duplication.
- **Simpler**: Fewer types to navigate, less code to maintain.
- **More robust**: One rendering path per pattern instead of N copy-paste implementations.
- **Still type-safe**: Parameterized subtypes are still sealed class members with compile-time exhaustiveness.

## Resolution

Spec updated in `doc/core/ContextForAgentProvider.md`. Collapsed 27 → 17 subtypes (spec-only change).

Expanded `InlineFileContentSection` scope beyond the original 2 to also absorb 4 additional
"read file + render under heading" types (`PlannerPublicMd`, `DoerOutputForReview`,
`PlannerFeedback`, `PlanReviewerPriorFeedback`) — these were structurally identical.

| New Parameterized Type | Replaces | Count |
|---|---|---|
| `OutputPathSection(label, path)` | `PlanFlowJsonOutputPath`, `PlanMdOutputPath`, `PublicMdOutputPath` | 3 |
| `InlineFileContentSection(heading, path)` | `PlanFlowJsonContent`, `PlanMdContent`, `PlannerPublicMd`, `DoerOutputForReview`, `PlannerFeedback`, `PlanReviewerPriorFeedback` | 6 |
| `FeedbackDirectorySection(dir, heading)` | `AddressedFeedback`, `RejectedFeedback`, `RemainingOptionalFeedback` | 3 |

No other spec docs referenced the collapsed type names — change is self-contained.

