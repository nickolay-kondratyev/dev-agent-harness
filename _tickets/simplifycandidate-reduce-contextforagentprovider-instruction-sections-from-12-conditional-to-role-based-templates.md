---
id: nid_orpcmdfj1m06lyajssewwn1k9_E
title: "SIMPLIFY_CANDIDATE: Reduce ContextForAgentProvider instruction sections from 12 conditional to role-based templates"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:31:13Z
status_updated_iso: 2026-03-15T01:31:13Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, context-assembly, instruction-provider]
---

## Current State
ContextForAgentProvider (doc/core/ContextForAgentProvider.md) assembles agent instructions using 12 sections with conditional sub-items (7a, 7b, 7c, 7d, 8a, 8b). Sections are conditionally included based on:
- Role (doer vs reviewer vs planner vs plan-reviewer)
- Iteration count (iteration > 1)
- Whether prior PUBLIC.md files exist
- Whether feedback files exist

This creates a combinatorial explosion of possible instruction file compositions.

## Proposed Simplification
Replace the 12-section conditional assembly with 3-4 pre-built role-based templates:
- DoerTemplate (first iteration)
- DoerIterationTemplate (iteration > 1, with feedback)
- ReviewerTemplate
- PlannerTemplate

Each template is a single, readable, self-contained instruction document with clear placeholder substitution points. The conditional logic moves from runtime section selection to template selection (a single if/when).

## Why This Improves Robustness
- Easier to audit what each role actually sees (read one template vs trace 12 conditional sections)
- Fewer code paths = fewer bugs in instruction assembly
- Templates are testable as whole units rather than section-by-section
- Reduces cognitive load for maintainers

## Spec references
- doc/core/ContextForAgentProvider.md (12-section instruction tables)
- doc/plan/granular-feedback-loop.md (reviewer sections 7a-7d)

