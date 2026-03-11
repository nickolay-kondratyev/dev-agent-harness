---
closed_iso: 2026-03-11T21:41:04Z
id: nid_gkkanv6kvkr4bzje1bitcek22_E
title: "Introduce DirectLLM tier interfaces (DirectQuickCheapLLM, DirectMediumLLM, DirectBudgetHighLLM)"
status: closed
deps: []
links: []
created_iso: 2026-03-11T20:43:48Z
status_updated_iso: 2026-03-11T21:41:04Z
type: feature
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [llm]
---

Per spec (doc/high-level.md lines 183-207), callers depend on tier interfaces, not on a specific model. Current code has only the base `DirectLLM` interface and a single `GLMHighestTierApi` implementation.

## What to create
Three tier interfaces extending `DirectLLM` in `app/src/main/kotlin/com/glassthought/ticketShepherd/core/supporting/directLLMApi/DirectLLM.kt`:
```kotlin
interface DirectQuickCheapLLM : DirectLLM   // fast, low-cost (title compression, slugification)
interface DirectMediumLLM : DirectLLM       // mid-tier (reserved, TBD in V1)
interface DirectBudgetHighLLM : DirectLLM   // expensive (FailedToConvergeUseCase summarization)
```

## What to update
- `DirectLlmInfra` in `app/src/main/kotlin/com/glassthought/ticketShepherd/core/initializer/Initializer.kt` (line 37-40): replace single `glmDirectLLM: DirectLLM` with tier-scoped fields (`quickCheap: DirectQuickCheapLLM`, `budgetHigh: DirectBudgetHighLLM`).
- `Initializer.initializeImpl`: wire concrete implementations to each tier. V1 assignments per spec:
  - `DirectQuickCheapLLM` → GLM-4.7-Flash (Z.AI)
  - `DirectBudgetHighLLM` → GLM-5 (Z.AI) (current GLMHighestTierApi)
  - `DirectMediumLLM` → can be deferred (no V1 callers yet)
- `GLMHighestTierApi` should implement `DirectBudgetHighLLM` (not bare `DirectLLM`).
- A new `GLMQuickCheapApi` (or similar) implementing `DirectQuickCheapLLM` for GLM-4.7-Flash.

## V1 callers
- `DirectQuickCheapLLM`: BranchNameBuilder (branch name compression via LLM) — needed for first `shepherd run`
- `DirectBudgetHighLLM`: FailedToConvergeUseCase (state summarization) — needed later but interface should exist now
- `DirectMediumLLM`: no V1 callers — interface only, no implementation required yet


## Notes

**2026-03-11T21:41:04Z**

Completed. All tier interfaces introduced:
- DirectQuickCheapLLM (GLM-4.7-Flash via GLMQuickCheapApi)
- DirectMediumLLM (interface only, no V1 callers)
- DirectBudgetHighLLM (GLM-5 via GLMHighestTierApi)
- GlmAnthropicCompatibleApi internal delegate avoids duplication
- DirectLlmInfra now has quickCheap + budgetHigh tier-scoped fields
- 150 tests passing. Commit: 5807ed0
