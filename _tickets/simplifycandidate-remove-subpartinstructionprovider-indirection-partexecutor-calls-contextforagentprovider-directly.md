---
id: nid_03j42oxwqnrqlcqfdbeyohox4_E
title: "SIMPLIFY_CANDIDATE: Remove SubPartInstructionProvider indirection — PartExecutor calls ContextForAgentProvider directly"
status: in_progress
deps: []
links: []
created_iso: 2026-03-15T01:24:13Z
status_updated_iso: 2026-03-17T19:25:27Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, interface-design, instruction-assembly]
---

## Problem

`SubPartInstructionProvider` (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) is an interface with two methods:
```kotlin
interface SubPartInstructionProvider {
    suspend fun assembleDoerInstructions(iterationNumber: Int, reviewerFeedbackPath: Path?): Path
    suspend fun assembleReviewerInstructions(iterationNumber: Int, doerOutputPath: Path): Path
}
```

This sits between `PartExecutor` and `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E), which has the actual assembly logic. The indirection exists to "decouple instruction assembly from execution."

However:
- `ContextForAgentProvider` is already the dedicated instruction assembler with clear interface methods.
- `SubPartInstructionProvider` is essentially a pass-through that maps executor-level concepts (iteration number, feedback path) to provider-level concepts.
- This adds an extra interface + implementation to maintain, test, and wire in `TicketShepherdCreator`.
- The mapping logic is trivial — it doesn't justify a separate abstraction layer.\n\n## Proposed Simplification\n\nHave `PartExecutor` call `ContextForAgentProvider` directly. The provider already knows about parts, sub-parts, roles, and iteration context (since it reads `current_state.json`).\n\nIf the mapping from executor concepts to provider concepts is non-trivial, it can be a private method inside the executor — no separate interface needed.\n\n## Benefits\n- **One fewer interface** to maintain and test.\n- **One fewer class** in the dependency wiring (`TicketShepherdCreator`).\n- **Simpler dependency graph** — executor depends on provider directly, not through an intermediary.\n- **No loss of testability** — `ContextForAgentProvider` itself can be faked/mocked in tests.\n- **More explicit** — reading the code, you see exactly what's being called without tracing through another layer.\n\n## Risk\nLow. The indirection was designed for decoupling, but the coupling between executor and instruction assembly is inherent (executors NEED instructions). An extra interface layer doesn't reduce this coupling meaningfully.\n\n## Spec files affected\n- `doc/core/PartExecutor.md`\n- `doc/core/ContextForAgentProvider.md`

