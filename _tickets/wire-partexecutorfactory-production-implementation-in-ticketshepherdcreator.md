---
id: nid_l32a31tdfjoe7ld0uurtum9g6_E
title: "Wire PartExecutorFactory production implementation in TicketShepherdCreator"
status: in_progress
deps: []
links: []
created_iso: 2026-03-20T00:45:53Z
status_updated_iso: 2026-03-20T00:49:23Z
type: feature
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [wiring, production, executor]
---

## Context

`TicketShepherdCreatorImpl` (line 135-137 of `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`) has a TODO stub for `PartExecutorFactory`:

```kotlin
private val partExecutorFactory: PartExecutorFactory = PartExecutorFactory {
    TODO("PartExecutorFactory not yet wired for production")
}
```

## What Needs to Happen

Implement a production `PartExecutorFactory` that creates `PartExecutorImpl` instances for a given `Part`.

### Interface
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactory.kt`
- `fun interface PartExecutorFactory { fun create(part: Part): PartExecutor }`

### Spec
- ref.ap.fFr7GUmCYQEV5SJi8p6AS.E (`doc/core/PartExecutor.md`)
- `PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) is the single implementation
- The factory must wire: `SubPartConfig` (doer + optional reviewer), `PartExecutorDeps`, `IterationConfig`
- Dependencies needed: `AgentFacade`, `ContextForAgentProvider`, `GitCommitStrategy`, `FailedToConvergeUseCase`, Granular Feedback Loop

### Existing Fakes (for reference)
- `TsPartExecutorFactory` in `app/src/test/kotlin/.../TicketShepherdTest.kt`

### Acceptance Criteria
- Production `PartExecutorFactory` impl wired in `TicketShepherdCreatorImpl` default parameter
- TODO stub removed
- Unit tests for factory logic
- `PartExecutorImpl` receives all required deps from `ShepherdContext`

### NOTE: This is a blocker
This is one of the highest-priority production wiring tasks. Both execution parts AND the planning phase (via `PlanningPartExecutorFactory`) depend on `PartExecutorImpl` being properly wirable.

