---
id: nid_qwz2s1lswafde3phqwst6fom6_E
title: "SIMPLIFY_CANDIDATE: Consolidate ContextForAgentProvider's 4 role-specific methods into one unified assembleInstructions(role, request)"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T22:01:45Z
status_updated_iso: 2026-03-17T22:05:41Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, dry, context, srp]
---

ContextForAgentProvider currently exposes 4 separate public methods, each with its own request type:
- assembleDoerInstructions(DoerInstructionRequest)
- assembleReviewerInstructions(ReviewerInstructionRequest)
- assemblePlannerInstructions(PlannerInstructionRequest)
- assemblePlanReviewerInstructions(PlanReviewerInstructionRequest)

The executor must know which method to call based on agent role — coupling executor to provider shape. Adding a new agent role requires adding a new method + request type.

Proposed simplification:
- Single public method: assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest)
- AgentRole sealed class or enum: DOER, REVIEWER, PLANNER, PLAN_REVIEWER
- UnifiedInstructionRequest with optional fields populated per role
- Provider internally dispatches per role — same underlying engine

Robustness gains:
- Executor has one provider method, not four — impossible to call wrong one
- New agent role types: add enum variant + plan, not a new interface method
- Strongly typed role parameter prevents silent bugs from wrong method call
- Interface surface shrinks by 3 methods — easier to test and mock

Relevant specs:
- doc/core/ContextForAgentProvider.md (interface section, request types)

Relevant code:
- app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt
- app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt
- PartExecutor (calls to provider)

