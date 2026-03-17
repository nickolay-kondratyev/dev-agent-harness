---
id: nid_r1mfznwnpynbq5t9rksza5q4h_E
title: "SIMPLIFY_CANDIDATE: Extract shared ReInstructWithRetry abstraction from PartExecutorImpl"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T21:04:44Z
status_updated_iso: 2026-03-17T21:43:54Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, part-executor, testing]
---

The pattern "re-instruct agent once with context, then treat as AgentCrashed" appears in 7+ distinct places inside PartExecutorImpl (and related components):

1. PUBLIC.md missing after `done` signal
2. PRIVATE.md missing after `self-compacted` signal
3. Feedback files missing in `pending/` after `needs_iteration`
4. Resolution marker (ADDRESSED/REJECTED) missing after doer `done`
5. Part completion guard — reviewer passes but critical/important feedback files remain
6. Bootstrap ACK re-delivery (handled via AckedPayloadSender — but re-instruction wrapper is still inline)
7. RejectionNegotiationUseCase has its own inline retry path

Each caller hand-rolls this logic with slightly different messages and file checks, leading to high cognitive load and inconsistent error paths.

**Opportunity:** Extract a `ReInstructWithRetry` abstraction (or similar) that:
- Takes: re-instruction content, the deferred to await, and a timeout
- Returns: success (signal received) or AgentCrashed (retry exhausted)
- Is independently unit-testable with FakeAgentFacade

This simplifies PartExecutorImpl (the most complex component per doc/core/PartExecutor.md) AND improves robustness by making every retry path uniform and tested.

Spec reference: doc/core/PartExecutor.md, doc/plan/granular-feedback-loop.md

