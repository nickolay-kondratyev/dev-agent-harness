---
closed_iso: 2026-03-17T19:30:40Z
id: nid_u0app7d6os21lhmcuxqoc1qoo_E
title: "SIMPLIFY_CANDIDATE: Remove SHARED_CONTEXT.md — fold shared context into plan.json or planning-phase PUBLIC.md"
status: closed
deps: [nid_orpcmdfj1m06lyajssewwn1k9_E]
links: []
created_iso: 2026-03-15T01:31:41Z
status_updated_iso: 2026-03-17T19:30:40Z
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

## Resolution

**Completed.** SHARED_CONTEXT.md removed from all specs (8 doc files) and all code (4 Kotlin files).

### What changed

**Specs updated (8 files):**
- `doc/schema/ai-out-directory.md`: Removed from directory tree, key files table, replaced "PUBLIC.md vs SHARED_CONTEXT.md" comparison with "PUBLIC.md vs PLAN.md", updated initial creation section
- `doc/core/ContextForAgentProvider.md`: Removed section 4 and section 11 from all 4 agent instruction tables (doer, reviewer, planner, plan-reviewer)
- `doc/high-level.md`: Removed from harness communication list and context assembly
- `doc/core/PartExecutor.md`: Removed from SubPartInstructionProvider table
- `doc/schema/plan-and-current-state.md`: Removed cross-reference
- `doc/use-case/HealthMonitoring.md`: Removed from FailedToConvergeUseCase summary inputs
- `doc/use-case/TicketFailureLearningUseCase.md`: Removed from agent reads list
- `doc_v2/resume.md`: Removed from instruction context description

**Code updated (4 files):**
- `ContextForAgentProvider.kt`: Removed `sharedContextPath` from `DoerInstructionRequest`, `ReviewerInstructionRequest`, `PlannerInstructionRequest`
- `ContextForAgentProviderImpl.kt`: Removed `sharedContextSection()` calls and `SHARED_CONTEXT_MD_GUIDELINES` from all builders
- `InstructionSections.kt`: Removed `sharedContextMdPath()`, `SHARED_CONTEXT_MD_GUIDELINES`. Updated `PUBLIC_MD_WRITING_GUIDELINES` to absorb shared knowledge guidance
- `ContextTestFixtures.kt`: Removed SHARED_CONTEXT.md file creation and `sharedContextPath` parameters

### Where shared knowledge goes now

1. **PUBLIC.md** — updated writing guidelines now include codebase discoveries, anchor points, cross-cutting constraints, and patterns
2. **PLAN.md** — architectural decisions and cross-cutting plan context (unchanged)
3. **Code itself** — WHY-NOT comments for durable reasoning (emphasized in updated guidelines)
4. **Persistent docs** — CLAUDE.md, deep memory, .md notes for reasoning that must survive beyond the workflow run (new guidance added)

### Net result
- 3 fewer parameters on request data classes
- 2 fewer sections assembled per agent instruction file (doer/reviewer/planner)
- 1 fewer file concept for agents to know about
- No ambiguity about SHARED_CONTEXT.md vs PUBLIC.md vs PLAN.md