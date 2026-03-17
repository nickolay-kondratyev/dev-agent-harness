---
id: nid_uevaeba713u1muroracpxh22j_E
title: "SIMPLIFY_CANDIDATE: Simplify SetupPlanUseCase — hide two-phase planning protocol from caller"
status: in_progress
deps: []
links: []
created_iso: 2026-03-15T01:14:17Z
status_updated_iso: 2026-03-17T20:42:42Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, interface-design, planning]
---

SetupPlanUseCase (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E, doc/use-case/SetupPlanUseCase/__this.md) currently returns a sealed class with two variants:
- `Ready(parts: List<Part>)` — static parts, no planning needed
- `NeedsPlanning(planningExecutor: PartExecutor, convertPlanToExecutionParts: suspend () -> List<Part>)` — carries a PartExecutor AND a suspend lambda

The caller (TicketShepherd) must then orchestrate two steps: (1) run planningExecutor.execute(), (2) call convertPlanToExecutionParts(). This pushes a two-phase protocol onto the caller, which must handle the sequencing and failure modes of both steps.

**Simplification:** Change SetupPlanUseCase to a single method: `suspend fun setup(): List<Part>`. The use case internally handles planning (run executor, convert plan, return parts) or returns static parts for straightforward workflows. The caller gets the same result type regardless of workflow mode.

**Robustness improvement:** The caller cannot misuse the two-phase protocol (e.g., calling convertPlanToExecutionParts without running the executor first, or forgetting to handle planning failures). Single responsibility: SetupPlanUseCase owns the full planning lifecycle.

**Spec files affected:** doc/use-case/SetupPlanUseCase/__this.md (simplify interface), doc/core/TicketShepherd.md (simplify planning section).

