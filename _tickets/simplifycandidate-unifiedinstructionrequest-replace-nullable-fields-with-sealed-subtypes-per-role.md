---
closed_iso: 2026-03-18T13:44:32Z
id: nid_s9yhhndwqn6xlu94lj55w7bzt_E
title: "SIMPLIFY_CANDIDATE: UnifiedInstructionRequest — replace nullable fields with sealed subtypes per role"
status: closed
deps: []
links: []
created_iso: 2026-03-18T02:09:44Z
status_updated_iso: 2026-03-18T13:44:32Z
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


## Notes

**2026-03-18T13:44:29Z**

Spec updated in doc/core/ContextForAgentProvider.md. Replaced UnifiedInstructionRequest (flat data class with ~15 nullable fields) with AgentInstructionRequest sealed class hierarchy: DoerRequest, ReviewerRequest (nested under ExecutionRequest), PlannerRequest, PlanReviewerRequest. Each subtype carries exactly the fields it needs — previously nullable role-specific fields are now non-nullable in their respective subtypes. assembleInstructions signature simplified from (role, request) to (request) — role is now encoded in the type. Internal when dispatch is compile-time exhaustive.
