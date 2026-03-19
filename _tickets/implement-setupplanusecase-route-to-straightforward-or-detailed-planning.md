---
id: nid_89a09ady4m86saemw4rul5b4s_E
title: "Implement SetupPlanUseCase — route to straightforward or detailed planning"
status: open
deps: [nid_kavqh23pdfq56cdli0fv4sm3u_E, nid_m3cm8xizw5qhu1cu3454rca79_E]
links: []
created_iso: 2026-03-19T00:39:32Z
status_updated_iso: 2026-03-19T00:39:32Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, ticket-shepherd, use-case]
---

## Context

Spec: `doc/use-case/SetupPlanUseCase/__this.md` (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E)

`SetupPlanUseCase` routes to the appropriate planning strategy based on workflow type and returns `List<Part>`.

## What to Implement

### 1. SetupPlanUseCase interface + implementation
```kotlin
interface SetupPlanUseCase {
    suspend fun setup(): List<Part>
}
```

### 2. Routing Logic
| Workflow type | Delegates to | Returns |
|---------------|-------------|--------|
| `straightforward` | Constructs `List<Part>` from workflow JSON static parts | `List<Part>` |
| `with-planning` | `DetailedPlanningUseCase` (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) | `List<Part>` — runs planning loop internally, returns execution parts |

### 3. Key Design Decisions
- Both workflow paths return `List<Part>` directly — no two-phase protocol.
- `SetupPlanResult` sealed class was intentionally removed (ref.ap.evYmpQfliHCHUTdK2QRgS.E).
- Planning complexity is fully encapsulated in `DetailedPlanningUseCase`.
- This is a simple routing class — delegates all real work downstream.

### 4. Dependencies
- `WorkflowDefinition` (nid_kavqh23pdfq56cdli0fv4sm3u_E) — to determine workflow type.
- `Part` data class (nid_m3cm8xizw5qhu1cu3454rca79_E) — the return type.
- `DetailedPlanningUseCase` for with-planning workflows (separate ticket).

### 5. Testing
- Unit tests with mocked/fake DetailedPlanningUseCase.
- Test straightforward routing returns static parts from workflow definition.
- Test with-planning routing delegates to DetailedPlanningUseCase.
- Test that unknown workflow type fails hard.

