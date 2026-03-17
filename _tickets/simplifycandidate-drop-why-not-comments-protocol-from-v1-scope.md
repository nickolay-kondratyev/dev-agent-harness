---
id: nid_o27zof98wzuwuc5c85x4q3vrb_E
title: "SIMPLIFY_CANDIDATE: Drop WHY-NOT Comments Protocol from V1 scope"
status: in_progress
deps: []
links: []
created_iso: 2026-03-15T01:13:56Z
status_updated_iso: 2026-03-17T19:29:05Z
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

