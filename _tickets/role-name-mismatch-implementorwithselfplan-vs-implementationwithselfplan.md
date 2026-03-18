---
closed_iso: 2026-03-18T15:25:25Z
id: nid_9q0r8y2gy8sumlsgmqqgjuqwg_E
title: "Role name mismatch: IMPLEMENTOR_WITH_SELF_PLAN vs IMPLEMENTATION_WITH_SELF_PLAN"
status: closed
deps: []
links: []
created_iso: 2026-03-18T15:02:58Z
status_updated_iso: 2026-03-18T15:25:25Z
type: bug
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [roles, config]
---

Spec example in doc/schema/plan-and-current-state.md line 466 and config/workflows/straightforward.json line 8 both reference role "IMPLEMENTOR_WITH_SELF_PLAN".

But the actual role file is _config/agents/_generated/IMPLEMENTATION_WITH_SELF_PLAN.md (and _config/agents/input/IMPLEMENTATION_WITH_SELF_PLAN.md).

One of these needs to be aligned. Per the spec, roles are auto-discovered from $TICKET_SHEPHERD_AGENTS_DIR/*.md filenames. So either:
- Rename the role file to IMPLEMENTOR_WITH_SELF_PLAN.md, OR
- Update the workflow JSON and spec example to use IMPLEMENTATION_WITH_SELF_PLAN

The fail-fast at startup (ref.ap.iF4zXT5FUcqOzclp5JVHj.E) would catch this at runtime.


## Notes

**2026-03-18T15:25:25Z**

Resolved by updating workflow JSON and spec doc to use IMPLEMENTATION_WITH_SELF_PLAN (matching actual role file names). Changed: config/workflows/straightforward.json line 8 and doc/schema/plan-and-current-state.md line 466. No Kotlin source changes needed.
