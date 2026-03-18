---
id: nid_5l14yzdlzcryi8d3bearl9k1r_E
title: "Missing PLANNER and PLAN_REVIEWER role definition files"
status: open
deps: []
links: []
created_iso: 2026-03-18T15:03:01Z
status_updated_iso: 2026-03-18T15:03:01Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [roles, catalog]
---

config/workflows/with-planning.json references roles PLANNER and PLAN_REVIEWER, but neither exists in:
- _config/agents/input/ (source)
- _config/agents/_generated/ (runtime)

Existing role files: IMPLEMENTATION.md, IMPLEMENTATION_REVIEWER.md, IMPLEMENTATION_WITH_SELF_PLAN.md

These need to be created before the with-planning workflow can run.
See: doc/high-level.md Agent Roles Directory Structure (ap.Q7kR9vXm3pNwLfYtJ8dZs.E)

