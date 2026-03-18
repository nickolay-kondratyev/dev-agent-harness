---
id: nid_s9yhhndwqn6xlu94lj55w7bzt_E
title: "SIMPLIFY_CANDIDATE: UnifiedInstructionRequest — replace nullable fields with sealed subtypes per role"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T02:09:44Z
status_updated_iso: 2026-03-18T13:42:21Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, compile-time-safety]
---

ref.ap.9HksYVzl1KkR9E1L2x8Tx.E (ContextForAgentProvider spec)

Currently UnifiedInstructionRequest has ~15 nullable fields discriminated by a role enum. Valid field combinations depend on which role is passed — this is a runtime concern.

Proposal: Replace with a sealed class hierarchy where each role (DOER, REVIEWER, PLANNER, PLAN_REVIEWER) gets a subtype with exactly its required fields.

Why simpler: No mental tracking of which nullable fields matter for which role. No need for runtime validation of "did I pass the right fields."
Why more robust: Invalid field combinations become compile errors. Cannot accidentally pass reviewer-only fields to a doer request.

File: doc/core/ContextForAgentProvider.md

