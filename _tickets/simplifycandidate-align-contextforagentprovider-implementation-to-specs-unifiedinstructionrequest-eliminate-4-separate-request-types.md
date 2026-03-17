---
id: nid_hg10syj90dgmoavzoflbm1nhj_E
title: "SIMPLIFY_CANDIDATE: Align ContextForAgentProvider implementation to spec's UnifiedInstructionRequest — eliminate 4 separate request types"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T22:47:02Z
status_updated_iso: 2026-03-17T22:59:23Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, dry, context, spec-alignment]
---

## Problem
The spec (doc/core/ContextForAgentProvider.md) defines a **unified interface**:

```kotlin
assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path
```

But the current implementation in `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` has 4 separate public methods and 4 separate request data classes:
- `assembleDoerInstructions(DoerInstructionRequest)`
- `assembleReviewerInstructions(ReviewerInstructionRequest)`
- `assemblePlannerInstructions(PlannerInstructionRequest)`
- `assemblePlanReviewerInstructions(PlanReviewerInstructionRequest)`

Each request type has 9-11 fields with heavy overlap (roleDefinition, ticketContent, iterationNumber, outputDir, publicMdOutputPath all appear in all 4).

## Proposed Simplification
Align to the spec:
1. Single `UnifiedInstructionRequest` data class with union of fields (role-specific fields nullable or with defaults)
2. Single `assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path` method
3. Role-specific `InstructionPlan` built via data-driven dispatch on `role`

## Why This Improves Both
- **Simpler**: 4 methods → 1; 4 request types → 1
- **More robust**: Adding a new agent role (e.g., V2 CRITIC) = adding one InstructionPlan, not a new method+request class+builder
- **Spec-aligned**: Code matches the documented architecture (reduces cognitive dissonance)

## Acceptance Criteria

- `ContextForAgentProvider` interface has single `assembleInstructions(role, request)` method
- `UnifiedInstructionRequest` replaces the 4 separate request data classes
- All instruction assembly tests pass
- Callers updated to use the new unified interface

