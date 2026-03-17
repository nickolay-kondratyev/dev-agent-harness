---
id: nid_12eb788ddd9frhmdob4s3bxoh_E
title: "SIMPLIFY_CANDIDATE: Collapse ContextForAgentProvider instruction assembly into data-driven sections"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:31:50Z
status_updated_iso: 2026-03-17T21:31:50Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, context-provider, robustness, dry]
---

Current design: 4 separate role-specific template methods (assembleDoerInstructions, assembleReviewerInstructions, assemblePlannerInstructions, assemblePlanReviewerInstructions), each reading through a role-specific section list. After granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) there will be a 5th path for per-feedback-item instructions.

Problem: When shared sections change (e.g., callback help text, PUBLIC.md writing guidelines), the change must be made in 4+ places. Each new role type adds another full copy of the section assembly logic.

Spec reference: doc/core/ContextForAgentProvider.md (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). The Doer table and Reviewer table share sections 1-5 and 8-10.

Simpler approach: Model each instruction section as an enum/sealed class (RoleDefinition, PartContext, Ticket, PlanMd, PriorPublicMd, IterationFeedback, FeedbackItem, OutputPath, WritingGuidelines, CallbackHelp). Each role defines its InstructionPlan as a List<InstructionSection>. A single internal assembleFromPlan(plan: InstructionPlan, request: InstructionRequest) walks the plan.

The public interface stays unchanged (4 type-safe methods with role-specific request types). Only the implementation changes.

Benefits:
- Single source of truth per section (DRY)
- Adding a new section or role involves declaring it once
- Reduces the chance of sections diverging between roles
- Makes the instruction structure legible as a data structure, not scattered across methods
- Easier to unit-test section assembly in isolation

