---
closed_iso: 2026-03-12T23:16:18Z
id: nid_q9l4ynk99ifr89sejxeagp36l_E
title: "Update WorkflowParser to align with new parts/sub-parts schema"
status: closed
deps: [nid_0vxob9hefy36mx7ughy93qw8i_E]
links: []
created_iso: 2026-03-11T01:39:19Z
status_updated_iso: 2026-03-12T23:16:18Z
type: task
priority: 2
assignee: nickolaykondratyev
tags: [schema, workflow, parser]
---

## Goal

Update `WorkflowParser` and workflow JSON definitions to use the new parts/sub-parts schema.

Currently `config/workflows/straightforward.json` and `config/workflows/with-planning.json` use the old structure (parts → phases → role). These need to align with the schema documented in `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E).

## Blocked By

- nid_0vxob9hefy36mx7ughy93qw8i_E (Iterate on plan.json and workflow schema) — schema must be finalized first.

## Key Files

- `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParser.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt`
- `config/workflows/straightforward.json`
- `config/workflows/with-planning.json`
- `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParserTest.kt`

