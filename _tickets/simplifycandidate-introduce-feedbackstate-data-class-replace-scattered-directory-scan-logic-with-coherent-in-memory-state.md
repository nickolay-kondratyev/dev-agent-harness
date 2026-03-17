---
id: nid_rxqvja0yzib7cc95v28081inv_E
title: "SIMPLIFY_CANDIDATE: Introduce FeedbackState data class — replace scattered directory-scan logic with coherent in-memory state"
status: open
deps: []
links: []
created_iso: 2026-03-17T22:15:32Z
status_updated_iso: 2026-03-17T22:15:32Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, feedback-loop, state]
---

The granular feedback loop (doc/plan/granular-feedback-loop.md) manages three directories (pending/, addressed/, rejected/) with state spread across multiple file-scan operations, resolution-marker parsing, and file-movement calls.

Problem:
- Feedback "state" is implicit: derived by scanning directories and reading markers inside files on each iteration.
- No single coherent snapshot of the feedback state — multiple partial reads during a single decision cycle create TOCTOU risk.
- Part-completion validation (no unresolved critical issues) requires re-scanning directories.
- Testing requires real file I/O.

Proposed simplification:
- Create a `FeedbackState` data class: `Map<severity, List<FeedbackItem>>` partitioned by resolution status.
- `FeedbackStateReader`: loads state once atomically at the start of each feedback-loop iteration.
- `FeedbackStateWriter`: persists state changes deterministically (single write method).
- All decisions (what to feed next, whether negotiation needed, part-complete guard) driven from the in-memory FeedbackState snapshot.
- Part-completion guard: `feedbackState.hasUnresolvedCritical()` — pure function, no I/O.

Robustness improvement:
- Eliminates TOCTOU risk from mid-cycle directory scans.
- Part-completion guard becomes a pure, testable function.
- Simpler testing: inject a programmatic FeedbackState, no file I/O in unit tests.
- Easier to audit: invariants (no critical pending = part done) enforced in one place.

Relevant specs: doc/plan/granular-feedback-loop.md, doc/schema/ai-out-directory.md (__feedback/ section)

