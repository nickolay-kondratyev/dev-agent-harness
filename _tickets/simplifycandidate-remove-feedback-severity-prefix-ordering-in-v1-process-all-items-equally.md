---
id: nid_zvk8lbj61cxakb6rh6szncdfk_E
title: "SIMPLIFY_CANDIDATE: Remove feedback severity prefix ordering in V1 — process all items equally"
status: open
deps: []
links: []
created_iso: 2026-03-18T15:09:45Z
status_updated_iso: 2026-03-18T15:09:45Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, feedback-loop, robustness]
---

## Problem

The granular feedback loop (`doc/plan/granular-feedback-loop.md`) introduces filename prefix conventions for severity encoding:
- `critical__<name>.md`
- `important__<name>.md`  
- `optional__<name>.md`

The harness then processes items in severity order: all critical first, then important, then optional.

This adds:
- Filename prefix parsing logic
- Severity enum/type
- Ordered processing algorithm (group by severity → process groups in order)
- Three conceptual tiers that the reviewer must understand and correctly apply
- Risk of reviewer mis-categorizing severity (critical written as important, etc.)

## Proposed Simplification

Process all feedback items equally, in filename alphabetical order:
- Reviewer writes items to `__feedback/pending/<name>.md` — no prefix convention
- Harness processes all items in the pending directory in alphabetical order
- If a reviewer considers feedback optional, they simply do not write it

## Why This Is Both Simpler AND More Robust
- **Simpler**: Removes prefix parsing, severity enum, ordered grouping logic, and the conceptual overhead of three tiers. The reviewer just writes what matters.
- **More robust**:
  - No risk of severity mis-categorization (wrong prefix = wrong processing order)
  - No ambiguity about whether an `optional__` item should even exist (if it is truly optional, why write it and waste a doer iteration?)
  - Reviewer naturally self-filters: items not worth the doer's time simply are not written\n- **Same effective outcome**: In practice, a good reviewer already writes the most important items. The severity prefix is instructions to the harness about something the reviewer already controls implicitly through their choice of what to write.\n- **Aligns with 80/20**: The ordering optimization provides marginal value (critical items are addressed first instead of... also being addressed, just in a different order). All items get addressed regardless of order.

