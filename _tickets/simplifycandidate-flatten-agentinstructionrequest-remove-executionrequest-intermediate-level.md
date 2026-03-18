---
id: nid_wxj0ewdf3fn6gqjzooqgdhec4_E
title: "SIMPLIFY_CANDIDATE: Flatten AgentInstructionRequest — remove ExecutionRequest intermediate level"
status: open
deps: []
links: []
created_iso: 2026-03-18T15:29:24Z
status_updated_iso: 2026-03-18T15:29:24Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

## Current State
In `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E), `AgentInstructionRequest` uses a 3-level sealed class hierarchy:

```
AgentInstructionRequest (sealed)
├── PlannerRequest
├── PlanReviewerRequest
└── ExecutionRequest (sealed)      ← intermediate level
    ├── DoerRequest
    └── ReviewerRequest
```

`ExecutionRequest` exists to share common fields (`partIndex`, `subPartIndex`, `iterationCount`, `priorPublicMd`) between `DoerRequest` and `ReviewerRequest`. However, this creates an extra layer of inheritance that requires `override` boilerplate and makes the when-expression matching less direct.

## Proposed Simplification
Flatten to 4 direct subtypes under `AgentInstructionRequest`:

```
AgentInstructionRequest (sealed)
├── PlannerRequest
├── PlanReviewerRequest
├── DoerRequest
└── ReviewerRequest
```

Shared fields between `DoerRequest` and `ReviewerRequest` can be extracted into a shared `data class ExecutionContext(partIndex, subPartIndex, iterationCount, priorPublicMd)` that both hold as a property (composition over inheritance).

## Why This Is Simpler AND More Robust
- **Less cognitive load**: Flat hierarchy is easier to understand at a glance.
- **No override boilerplate**: Direct properties instead of inherited+overridden ones.
- **Exhaustive when without nesting**: `when(request)` directly matches all 4 types.
- **Composition over inheritance**: Shared data via composition (`executionContext` property) is more Kotlin-idiomatic.
- **Same compile-time safety**: Sealed class still enforces exhaustive matching.

## Affected Specs
- `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E)

