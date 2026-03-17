---
id: nid_fh3cqdak5erqyxp748a8f135i_E
title: "SIMPLIFY_CANDIDATE: Inline StraightforwardPlanUseCase into SetupPlanUseCase — eliminate near-empty class"
status: open
deps: []
links: []
created_iso: 2026-03-17T23:41:41Z
status_updated_iso: 2026-03-17T23:41:41Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec, setup-plan]
---

## Problem

`StraightforwardPlanUseCase` (ref.ap.6iySKY6iakspLNi3WenRO.E) is a near-empty class. The spec says it "just gives out a predefined schema" — it reads parts from the static workflow JSON and returns them. This is a single-expression operation.

`SetupPlanUseCase` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E) routes between `StraightforwardPlanUseCase` and `DetailedPlanningUseCase` based on workflow type.

Having a separate class for a single-expression routing target adds:
- An extra class file
- An extra constructor dependency on `SetupPlanUseCase`
- An extra interface (if one exists)
- Indirection for a trivial operation

## Simplification

Inline the straightforward logic into `SetupPlanUseCase.setup()` as a `when` branch:

```kotlin
suspend fun setup(): List<Part> = when (workflowDefinition.type) {
    WorkflowType.STRAIGHTFORWARD -> workflowDefinition.parts  // direct return
    WorkflowType.WITH_PLANNING -> detailedPlanningUseCase.execute()
}
```

The `with-planning` path legitimately needs a separate class (`DetailedPlanningUseCase`) because it owns a complex lifecycle (executor creation, plan conversion, retry on validation failure). The straightforward path does not.

### What this eliminates
- `StraightforwardPlanUseCase` class and file
- One constructor dependency on `SetupPlanUseCase`
- One level of indirection for the common case

### What it preserves
- `DetailedPlanningUseCase` remains a separate class (justified complexity)
- `SetupPlanUseCase` remains the routing entry point
- All behavior identical

## Why This Improves Robustness

- Less code to maintain — trivial operations don't need their own class\n- Easier to understand the routing: one `when` branch instead of following a delegation chain\n- Follows YAGNI — if the straightforward path grows complex, it can be extracted back out\n\n## Affected Specs\n\n- `doc/use-case/SetupPlanUseCase/__this.md` — routing table\n- `doc/use-case/SetupPlanUseCase/StraightforwardPlanUseCase.md` — would be removed or merged

