# Exploration: SetupPlanUseCase

## Key Findings

- `WorkflowDefinition` at `core/workflow/WorkflowDefinition.kt` — has `isStraightforward` / `isWithPlanning` booleans, `parts` (for straightforward) and `planningParts` (for with-planning)
- `Part` data class at `core/state/Part.kt` — simple data class with name, phase, description, subParts
- `DetailedPlanningUseCase` at `usecase/planning/DetailedPlanningUseCase.kt` — already implemented as `fun interface`, returns `List<Part>`
- Pattern: fun interface + Impl class, constructor injection, structured logging via OutFactory
- StraightforwardPlanUseCase spec says: return predefined parts from WorkflowDefinition

## Implementation Plan

1. Create `StraightforwardPlanUseCase` (fun interface + impl) — returns `workflowDefinition.parts!!`
2. Create `SetupPlanUseCase` (fun interface + impl) — routes based on `workflowDefinition.isStraightforward`
3. Unit tests for both
