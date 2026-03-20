---
id: nid_ygnxc2gdnpjb8niouyg794r1g_E
title: "Wire FinalCommitUseCase production implementation in TicketShepherdCreator"
status: in_progress
deps: []
links: [nid_vrghoub55m7ypcua36lj437hg_E]
created_iso: 2026-03-20T00:46:22Z
status_updated_iso: 2026-03-20T00:49:59Z
type: feature
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [wiring, production, git]
---

## Context

`TicketShepherdCreatorImpl` (line 138-140 of `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`) has a TODO stub for `FinalCommitUseCase`:

```kotlin
private val finalCommitUseCase: FinalCommitUseCase = FinalCommitUseCase {
    TODO("FinalCommitUseCase not yet wired for production")
}
```

## What Needs to Happen

Implement a production `FinalCommitUseCase` that performs a final git commit after all parts complete.

### Interface
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCase.kt`
- `fun interface FinalCommitUseCase { suspend fun commitIfDirty() }`

### Spec (from `doc/core/TicketShepherd.md` — workflow success step 4a)
- Run `git add -A && git commit` to capture any remaining state (e.g., final `current_state.json` flush)
- **Skip (no-op)** if the working tree is clean — no changes since last commit
- Called after all parts complete successfully, before `TicketStatusUpdater.markDone()`
- Also ref.ap.BvNCIzjdHS2iAP4gAQZQf.E (`doc/core/git.md`) for git conventions

### Implementation Details
- Check `git status --porcelain` — if empty, return (no-op)
- Otherwise: `git add -A` then `git commit -m "<appropriate message>"`
- Use `ProcessRunner` from asgardCore for git command execution
- Commit message should indicate this is a final state capture (e.g., "Final state commit")

### Existing Fakes (for reference)
- `TsFinalCommitUseCase` in `app/src/test/kotlin/.../TicketShepherdTest.kt`

### Acceptance Criteria
- Production `FinalCommitUseCaseImpl` created
- Wired in `TicketShepherdCreatorImpl` default parameter
- TODO stub removed
- Unit tests verifying: commits when dirty, no-ops when clean
- Integration test verifying actual git operations (gated by `isIntegTestEnabled()`)

