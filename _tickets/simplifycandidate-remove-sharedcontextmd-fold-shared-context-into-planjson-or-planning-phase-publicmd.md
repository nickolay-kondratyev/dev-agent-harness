---
id: nid_u0app7d6os21lhmcuxqoc1qoo_E
title: "SIMPLIFY_CANDIDATE: Remove SHARED_CONTEXT.md — fold shared context into plan.json or planning-phase PUBLIC.md"
status: open
deps: [nid_orpcmdfj1m06lyajssewwn1k9_E]
links: []
created_iso: 2026-03-15T01:31:41Z
status_updated_iso: 2026-03-15T01:31:41Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, ai-out, file-schema]
---

## Current State
The .ai_out/ directory schema (doc/schema/ai-out-directory.md) defines SHARED_CONTEXT.md as a try-level file shared across all parts. It captures cross-cutting context (e.g., architectural decisions from planning).

This exists alongside:
- plan.json (captures the overall plan structure)
- Per-sub-part PUBLIC.md files (each sub-part output, visible to subsequent parts via ContextForAgentProvider)
- PLAN.md (human-readable plan)

## Proposed Simplification
Remove SHARED_CONTEXT.md. Cross-cutting context from the planning phase is already captured in:
1. PLAN.md — the human-readable plan document
2. The planning sub-part PUBLIC.md — the planner agent output
3. plan.json — structured plan data

If architectural decisions or shared context need to persist, they go into the planner PUBLIC.md or a dedicated section of PLAN.md, both of which are already assembled by ContextForAgentProvider for execution agents.

## Why This Improves Robustness
- One fewer file concept for agents to know about and write to
- Eliminates ambiguity about what goes in SHARED_CONTEXT.md vs PUBLIC.md vs PLAN.md
- Reduces ContextForAgentProvider assembly logic (one fewer section to concatenate)
- Fewer files = fewer I/O operations, fewer existence checks

## Trade-off
- Loss of a dedicated cross-cutting context file. Mitigated by PLAN.md already serving this role.

## Spec references
- doc/schema/ai-out-directory.md (SHARED_CONTEXT.md definition)
- doc/core/ContextForAgentProvider.md (instruction assembly includes SHARED_CONTEXT.md)

--------------------------------------------------------------------------------
OK lets simplify. 

BUT let's make sure the agents are properly instructed to capture the reasoning within the code files and persistent documentation (CLAUDE.md, deep memory, md notes) they create/edit so that the reasoning survives beyond the current iteration. 