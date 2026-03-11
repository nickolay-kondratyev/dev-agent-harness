---
id: nid_0vxob9hefy36mx7ughy93qw8i_E
title: "Iterate on plan.json and workflow schema"
status: open
deps: []
links: []
created_iso: 2026-03-11T01:39:11Z
status_updated_iso: 2026-03-11T01:39:11Z
type: task
priority: 2
assignee: nickolaykondratyev
tags: [schema, workflow]
---

## Goal

Refine the plan.json and workflow definition schema before updating the parser.

The current schema direction is documented in `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E).

Key design decisions to finalize:
- Sub-part structure with iteration config and `loopsBackTo`
- Role and agent type metadata in plan.json
- How straightforward vs with-planning workflows map to the new parts/sub-parts model
- Whether `config/workflows/straightforward.json` and `config/workflows/with-planning.json` need schema changes

This ticket is a prerequisite for updating the WorkflowParser.

