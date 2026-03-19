---
id: nid_itogi6ji82dbhb0k3zzt6v8qp_E
title: "Implement TicketShepherdCreator — ticket-scoped wiring factory"
status: in_progress
deps: [nid_xeq8q9q7xmr56x5ttr98br4z9_E, nid_kavqh23pdfq56cdli0fv4sm3u_E, nid_00027pdr09egw4v9btb4vl6z7_E, nid_156yspl7869zrisam6l6xrd66_E, nid_fjod8du6esers3ajur2h7tvgx_E, nid_o5azwgdl76nnofttpt7ljgkua_E, nid_7xzhkw4pw5sc5hqh80cvsotdc_E, nid_p1w49sk0s2isnvcjbmhgapho7_E, nid_fqzi45z9xja51yy4yk3m3mkwh_E, nid_v14amda2uv5nedrp9hvb8xlfq_E, nid_mebn70o7xjiabzx5uxngjx8uf_E, nid_d1qyvonxndnk9yp68czrb98ki_E]
links: [nid_7xzhkw4pw5sc5hqh80cvsotdc_E]
created_iso: 2026-03-19T00:10:36Z
status_updated_iso: 2026-03-19T22:58:44Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [core, wiring, creator]
---

## Context

Spec: `doc/core/TicketShepherdCreator.md` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)

`TicketShepherdCreator` receives `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E) plus ticket-specific
inputs (ticket path, workflow name), resolves ticket-scoped dependencies, and returns a ready-to-go
`TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E). One shepherd per run — the creator is called once
from the `Initializer` (ref.ap.HRlQHC1bgrTRyRknP3WNX.E).

## Inputs
- `ShepherdContext` — shared infrastructure (tmux, logging, use cases), already initialized
- `ticketPath: Path` — the markdown ticket file
- `workflowName: String` — which workflow JSON to load

## What It Does (sequential steps)

1. **Resolve workflow JSON** — calls `WorkflowParser` to load `config/workflows/<name>.json`
   (depends on nid_kavqh23pdfq56cdli0fv4sm3u_E)
2. **Parse ticket** — calls existing `TicketParser.parse(path)` to get `TicketData`
3. **Validate ticket frontmatter** — fail hard if `id`, `title`, or `status` are missing/empty.
   Print clear error naming the missing field(s) and ticket path.
4. **Validate ticket status** — `status` must be `in_progress`. Fail hard with clear error if not.
5. **Validate working tree** — calls `WorkingTreeValidator` (nid_00027pdr09egw4v9btb4vl6z7_E)
6. **Record originating branch** — `GitBranchManager.getCurrentBranch()` before checkout.
   Store as `originatingBranch` on `TicketShepherd`.
7. **Resolve try-N** — calls `TryNResolver` (nid_156yspl7869zrisam6l6xrd66_E) to find first
   unused N from `.ai_out/` directories.
8. **Create feature branch** — `git checkout -b {TICKET_ID}__{slug}__try-{N}` via `GitBranchManager`
   (already implemented: `GitBranchManager`, `BranchNameBuilder`).
9. **Set up .ai_out/` directory** — calls `AiOutputStructure.ensureStructure()`
   (nid_fjod8du6esers3ajur2h7tvgx_E, wired via nid_7xzhkw4pw5sc5hqh80cvsotdc_E).
10. **Create in-memory CurrentState** — from workflow definition
    (nid_o5azwgdl76nnofttpt7ljgkua_E). For `with-planning` workflows, planning part from
    `planningParts`; for `straightforward`, execution parts directly. Flush to
    `current_state.json`.
11. **Construct Clock** — `SystemClock` for production
    (nid_xeq8q9q7xmr56x5ttr98br4z9_E).
12. **Construct AgentFacadeImpl** — wires `SessionsState`, `AgentTypeAdapter`,
    `TmuxSessionManager`, `TmuxCommunicator`, `ContextWindowStateReader`,
    `UserQuestionHandler` (ref.ap.9h0KS4EOK5yumssRCJdbq.E).
13. **Wire ContextForAgentProvider** — already implemented
    (`ContextForAgentProviderImpl`) — pass .ai_out/ paths and role catalog.
14. **Construct TicketShepherd** — pass `ShepherdContext` + all ticket-scoped state +
    `originatingBranch` + `tryNumber`.

## Class Structure

```kotlin
interface TicketShepherdCreator {
    suspend fun create(
        shepherdContext: ShepherdContext,
        ticketPath: Path,
        workflowName: String,
    ): TicketShepherd
}
```

- Package: `com.glassthought.shepherd.core.creator`
- Constructor-inject: `WorkflowParser`, `TicketParser`, `WorkingTreeValidator`,
  `TryNResolver`, `GitBranchManager`, `BranchNameBuilder`
- The creator constructs `AgentFacadeImpl`, `CurrentState`, `ContextForAgentProvider`,
  and `TicketShepherd` internally — these are ticket-scoped, not shared.

## Error Handling
- All validation errors fail hard with `IllegalStateException` and clear messages
- No partial cleanup needed — if branch creation fails, nothing was created yet
- If later steps fail, the branch exists but no resources to clean up

## Tests
- Ticket with missing `id` → clear error
- Ticket with missing `status` → clear error
- Ticket with `status \!= in_progress` → clear error
- Clean working tree → proceeds
- Dirty working tree → clear error with file list
- Straightforward workflow → CurrentState has execution parts
- With-planning workflow → CurrentState has planning part only
- Originating branch recorded correctly
- Try-N resolution finds first available number
- Feature branch created with correct name format

## Files to create
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`
  (interface + impl)
- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`

## Not in scope
- TicketShepherd orchestration logic (separate spec ref.ap.P3po8Obvcjw4IXsSUSU91.E)
- AgentFacadeImpl internals (separate spec ref.ap.9h0KS4EOK5yumssRCJdbq.E)
- Initializer (ref.ap.HRlQHC1bgrTRyRknP3WNX.E) — that calls this creator


## Notes

**2026-03-19T00:55:26Z**

Added 5 missing deps from ticket tree review:
- nid_p1w49sk0s2isnvcjbmhgapho7_E (AgentFacadeImpl) — step 12 constructs it
- nid_fqzi45z9xja51yy4yk3m3mkwh_E (TicketShepherd) — step 14 constructs it
- nid_v14amda2uv5nedrp9hvb8xlfq_E (SessionsState) — step 12 wires into AgentFacadeImpl
- nid_mebn70o7xjiabzx5uxngjx8uf_E (ContextWindowStateReader) — step 12 wires into AgentFacadeImpl
- nid_d1qyvonxndnk9yp68czrb98ki_E (StdinUserQuestionHandler) — step 12 wires into AgentFacadeImpl
