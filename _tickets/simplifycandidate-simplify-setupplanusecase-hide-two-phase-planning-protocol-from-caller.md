---
closed_iso: 2026-03-17T20:47:33Z
id: nid_uevaeba713u1muroracpxh22j_E
title: "SIMPLIFY_CANDIDATE: Simplify SetupPlanUseCase — hide two-phase planning protocol from caller"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:14:17Z
status_updated_iso: 2026-03-17T20:47:33Z
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

---

## Resolution

### Changes Made (spec-only, 3 files)

1. **`doc/use-case/SetupPlanUseCase/__this.md`**: Replaced `SetupPlanResult` sealed class interface with `suspend fun setup(): List<Part>`. Both routing table entries now return `List<Part>`. Marked `SetupPlanResult` (ap.evYmpQfliHCHUTdK2QRgS.E) as REMOVED.

2. **`doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md`**: Changed from "creates executor and hands back to caller" to "owns full planning lifecycle". Now documents: create executor → run it → handle PartResult → kill TMUX sessions → convert plan → return List<Part>. PlanConversionException retry loop is now internal to DetailedPlanningUseCase (was TicketShepherd's responsibility).

3. **`doc/core/TicketShepherd.md`**: Removed old steps 3a-3e (the two-phase NeedsPlanning protocol). Step 2 now simply calls `SetupPlanUseCase.setup()` → `List<Part>`. Updated "What TicketShepherd Does NOT Do" section. Updated FailedToExecutePlanUseCase paragraph (planning failures are now internal to DetailedPlanningUseCase). Renumbered steps.

