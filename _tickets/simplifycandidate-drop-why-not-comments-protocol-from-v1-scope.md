---
closed_iso: 2026-03-17T19:32:11Z
id: nid_o27zof98wzuwuc5c85x4q3vrb_E
title: "SIMPLIFY_CANDIDATE: Drop WHY-NOT Comments Protocol from V1 scope"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:13:56Z
status_updated_iso: 2026-03-17T19:32:11Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, context-assembly, v1-scope]
---

The WHY-NOT Comments Protocol in ContextForAgentProvider (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E, doc/core/ContextForAgentProvider.md) specifies a full sub-protocol for how agents write code comments: a specific format, three trigger sources (reviewer instruction, doer self-discovery, cross-try learning), non-immutability semantics, and reviewer validation of comment placement.

This is over-engineering for V1. It adds implementation surface to ContextForAgentProvider (additional instruction sections, reviewer validation criteria) for what is essentially "write good comments."

**Simplification:** Replace the full protocol with a single instruction line in the agent context: "Document non-obvious decisions and pitfalls in code comments." This covers 80% of the value. The structured format, three-source triggers, and reviewer validation can be added post-V1 if there is evidence agents produce poor comments without the protocol.

**Robustness improvement:** Fewer instructions = less chance of agent confusion from instruction overload. Simpler reviewer criteria = more consistent reviews.

**Spec files affected:** doc/core/ContextForAgentProvider.md (remove WHY-NOT Comments section and related reviewer instruction sections).

## Resolution

Completed. Replaced the full WHY-NOT Comments Protocol with a single instruction line in the doer pushback guidance: "Document non-obvious decisions and pitfalls in code comments."

### Changes made (spec only):
- **doc/core/ContextForAgentProvider.md**: Removed entire `## WHY-NOT Comments Protocol / ap.kmiKk7vECiNSpJjAXYMyE.E` section (~50 lines), removed WHY-NOT reminder row (8b) from doer instruction table, removed `## WHY-NOT Pitfalls to Document` from reviewer PUBLIC.md template, simplified doer pushback guidance to use a single bullet point instead of a full sub-protocol.
- **doc/high-level.md**: Simplified vocabulary table row from structured protocol to "Agents document non-obvious decisions and pitfalls in code comments." Removed "WHY-NOT comments protocol" from linked docs description.
- **doc/plan/granular-feedback-loop.md**: Removed `ref.ap.kmiKk7vECiNSpJjAXYMyE.E` references from rejection instruction text.

### Follow-up:
- Created ticket nid_hzyl1737cj1dn919ndbn0gln3_E for cleaning up stale code references to the removed anchor point in `ProtocolVocabulary.kt` and `InstructionSections.kt`.

