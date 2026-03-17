---
id: nid_f2qmgl7d5u803hhiks4hd7fkt_E
title: "SIMPLIFY_CANDIDATE: Eliminate loadsPlan boolean — always include PLAN.md for all doer sub-parts"
status: open
deps: []
links: []
created_iso: 2026-03-17T23:13:28Z
status_updated_iso: 2026-03-17T23:13:28Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

In doc/schema/plan-and-current-state.md, the `loadsPlan` field on sub-parts is a planner-controlled boolean that determines whether PLAN.md is included in a sub-part's instruction assembly. The harness validates that "at least one implementor sub-part has loadsPlan: true" (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E).\n\n**Problem:** This field adds complexity without clear value:\n1. The planner must remember to set `loadsPlan: true` on at least one sub-part\n2. The harness must validate this constraint (convertPlanToExecutionParts)\n3. ContextForAgentProvider must conditionally include/exclude PLAN.md based on this flag\n4. If the planner forgets to set it, the validation fails — a preventable failure mode\n\n**Proposed simplification:** Eliminate `loadsPlan` entirely. In `with-planning` workflows, always include PLAN.md in instruction assembly for ALL doer sub-parts.\n\n**Why this works just as well or better:**\n- PLAN.md was written FOR implementation agents. Every implementor benefits from seeing the plan.\n- There is no legitimate reason to hide the plan from an implementor sub-part\n- The plan is a read-only context document — including it when not strictly needed costs only prompt tokens, not correctness\n\n**Robustness improvement:**\n- Eliminates a planner-controlled field that can be misconfigured\n- Removes the validation step that catches this misconfiguration (no need to validate what can't go wrong)\n- Removes conditional logic in ContextForAgentProvider — PLAN.md is always included for with-planning workflows, period\n\nAffected specs:\n- doc/schema/plan-and-current-state.md (SubPart Fields, loadsPlan definition)\n- doc/core/ContextForAgentProvider.md (PlanMd section, conditional inclusion)\n- doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md (validation of loadsPlan)

