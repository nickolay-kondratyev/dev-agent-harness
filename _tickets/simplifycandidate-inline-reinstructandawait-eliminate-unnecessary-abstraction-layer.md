---
closed_iso: 2026-03-18T15:33:24Z
id: nid_pqml7kyou1qfggya49bgplqlr_E
title: "SIMPLIFY_CANDIDATE: Inline ReInstructAndAwait — eliminate unnecessary abstraction layer"
status: closed
deps: []
links: []
created_iso: 2026-03-18T15:29:51Z
status_updated_iso: 2026-03-18T15:33:24Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

## Current State
In `doc/use-case/ReInstructAndAwait.md`, `ReInstructAndAwaitUseCase` is a dedicated abstraction that wraps the pattern of:
1. Calling `agentFacade.sendPayloadAndAwaitSignal()`
2. Mapping the resulting `AgentSignal` to `ReInstructOutcome` (Responded, FailedWorkflow, Crashed)

This use case exists to share code between the granular feedback loop and rejection negotiation in `doc/plan/granular-feedback-loop.md`.

## Proposed Simplification
Eliminate `ReInstructAndAwaitUseCase` and `ReInstructOutcome`. Instead:
- Call sites use `agentFacade.sendPayloadAndAwaitSignal()` directly
- A small private utility function (or extension function) handles the common `AgentSignal → action` mapping (~5-10 lines)

## Why This Is Simpler AND More Robust
- **Removes an abstraction layer**: `ReInstructOutcome` is semantically equivalent to a subset of `AgentSignal` — it adds a type without adding meaning.
- **Less indirection**: Call sites directly work with `AgentSignal`, which they already understand from the executor context.
- **Fewer types to maintain**: One less sealed class (`ReInstructOutcome`) and one less use case class.
- **Marginal DRY savings**: The shared code is ~15 lines. A private function achieves the same DRY benefit without the extra abstraction.
- **No robustness loss**: The mapping logic is trivial and well-tested regardless of where it lives.

## Affected Specs
- `doc/use-case/ReInstructAndAwait.md` — would be removed entirely
- `doc/plan/granular-feedback-loop.md` — call sites updated to use AgentFacade directly
- `doc/core/PartExecutor.md` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) — if referenced

