---
closed_iso: 2026-03-17T18:28:34Z
id: nid_za6sjcg45jsexku4xi5lwmxh5_E
title: "SIMPLIFY_CANDIDATE: Merge SingleDoerPartExecutor into DoerReviewerPartExecutor with optional reviewer"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:24:10Z
status_updated_iso: 2026-03-17T18:28:34Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, part-executor, dry]
---

## Problem

Two separate `PartExecutor` implementations exist:
- `SingleDoerPartExecutor` — no reviewer, no iteration loop.
- `DoerReviewerPartExecutor` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) — full doer/reviewer iteration cycle.

Per the spec (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E), both share:
- The same health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E)
- The same `AgentSignal` handling
- The same PUBLIC.md validation (ref.ap.THDW9SHzs1x2JN9YP9OYU.E)
- The same git commit strategy hooks
- The same late-fail-workflow checkpoints

The only difference is whether a reviewer sub-part exists.

## Proposed Simplification

Merge into a single executor class. When a part has no reviewer sub-part (null/absent in config), the executor simply completes after the doer's `COMPLETED` signal. No separate class needed.\n\n```kotlin\nclass PartExecutorImpl(\n    private val doerConfig: SubPartConfig,\n    private val reviewerConfig: SubPartConfig?,  // null = no reviewer\n    ...\n) : PartExecutor\n```\n\n## Benefits\n- **Eliminates code duplication** — health-aware loop, PUBLIC.md validation, git hooks, and late-fail-workflow checks exist in one place.\n- **Single class to test** — reduces test surface area.\n- **Simpler factory** — `TicketShepherd` doesn't need to choose which executor type to construct.\n- **More robust** — bug fixes to the shared logic apply everywhere automatically.\n\n## Risk\nLow. The reviewer-absent path is the trivial subset of the reviewer-present path.\n\n## Spec files affected\n- `doc/core/PartExecutor.md`

## Resolution

**Completed.** Merged `SingleDoerPartExecutor` and `DoerReviewerPartExecutor` into a single
`PartExecutorImpl` class with an optional reviewer (`reviewerConfig: SubPartConfig?`).

### What Changed
- **`doc/core/PartExecutor.md`** (primary): `DoerReviewerPartExecutor` section renamed to
  `PartExecutorImpl`, added constructor signature with nullable `reviewerConfig`, added
  "Doer-Only Path" subsection describing the `reviewerConfig == null` behavior, removed
  standalone `SingleDoerPartExecutor` section.
- **13 secondary spec files** updated: all references to `DoerReviewerPartExecutor` and
  `SingleDoerPartExecutor` replaced with `PartExecutorImpl` (with contextual notes like
  "with reviewer" or "`reviewerConfig = null`" where clarifying).
- **Anchor point `ap.mxIc5IOj6qYI7vgLcpQn5.E`** preserved — reassigned from
  `DoerReviewerPartExecutor` to `PartExecutorImpl`.

### Key Design
- When `reviewerConfig == null`: spawn doer → health-aware await → PUBLIC.md validation →
  map signal to `PartResult`. No iteration, no reviewer.
- When `reviewerConfig != null`: full doer/reviewer iteration loop (unchanged behavior).
- Single class to test, single class for `TicketShepherd` to construct.
