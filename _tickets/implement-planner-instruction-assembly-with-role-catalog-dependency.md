---
id: nid_zsypyth6m472zk1qg5r1cnl6o_E
title: "Implement planner instruction assembly with role catalog dependency"
status: open
deps: [nid_m5e0q3oslsihsxsz1h6no6nwr_E]
links: []
created_iso: 2026-03-12T23:20:03Z
status_updated_iso: 2026-03-12T23:20:03Z
type: feature
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [planner, instructions, context-assembly]
---

Implement ContextForAgentProvider.assemblePlannerInstructions() — the planner agent instruction assembly.

Planner instructions include (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E):
1. Role definition (PLANNER role file from $TICKET_SHEPHERD_AGENTS_DIR)
2. Ticket content
3. SHARED_CONTEXT.md
4. **Role catalog** — all RoleDefinition entries (name + description + description_long) so planner can assign roles to sub-parts
5. Available agent types & models (V1: ClaudeCode only, models: opus, sonnet)
6. Plan format instructions (JSON schema for plan.json, ref.ap.56azZbk7lAMll0D4Ot2G0.E)
7. Reviewer feedback (on iteration > 1) — PLAN_REVIEWER PUBLIC.md
8. plan.json output path
9. PLAN.md output path
10. PUBLIC.md output path
11. PUBLIC.md writing guidelines
12. Callback script usage (including validate-plan)

This depends on the role catalog being designed and implemented first, since the planner needs the full role catalog to assign roles to sub-parts.

Spec references:
- ContextForAgentProvider: doc/core/ContextForAgentProvider.md (ap.9HksYVzl1KkR9E1L2x8Tx.E)
- Plan schema: doc/schema/plan-and-current-state.md (ap.56azZbk7lAMll0D4Ot2G0.E)
- Agent type & model assignment: doc/high-level.md (ap.Xt9bKmV2wR7pLfNhJ3cQy.E)
- Callback scripts: doc/core/agent-to-server-communication-protocol.md (ap.wLpW8YbvqpRdxDplnN7Vh.E)
- validate-plan endpoint: (ap.R8mNvKx3wQ5pLfYtJ7dZe.E)

