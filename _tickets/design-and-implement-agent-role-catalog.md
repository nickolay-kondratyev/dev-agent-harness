---
closed_iso: 2026-03-18T14:56:20Z
id: nid_m5e0q3oslsihsxsz1h6no6nwr_E
title: "Design and implement agent role catalog"
status: closed
deps: []
links: []
created_iso: 2026-03-12T23:19:46Z
status_updated_iso: 2026-03-18T14:56:20Z
type: feature
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [roles, catalog]
---

## Resolution: CLOSED — Already merged

All information from this ticket was already present in the specs at the time of review (2026-03-18).

### Verification — every ticket requirement mapped to spec:

| Requirement | Spec Location |
|---|---|
| Role definitions in `_config/agents/input/*.md` (authoritative source) | `doc/high-level.md` — Agent Roles Directory Structure (ap.Q7kR9vXm3pNwLfYtJ8dZs.E) |
| `generate.sh` → `_config/agents/_generated/*.md` (runtime files) | `doc/high-level.md` — Agent Roles Directory Structure (ap.Q7kR9vXm3pNwLfYtJ8dZs.E) |
| `$TICKET_SHEPHERD_AGENTS_DIR` → `_generated/` | `doc/high-level.md` lines 428-432 + `doc/core/git.md` Required Env Vars table |
| YAML frontmatter: `description` (required), `description_long` (optional) | `doc/high-level.md` — Role Catalog (ap.iF4zXT5FUcqOzclp5JVHj.E) |
| Roles = behavior only, no agentType/model | `doc/high-level.md` — Role Catalog + Agent Type & Model Assignment (ap.Xt9bKmV2wR7pLfNhJ3cQy.E) |
| Auto-discovered at startup | `doc/high-level.md` — Role Catalog (ap.iF4zXT5FUcqOzclp5JVHj.E) |
| Fail-fast on startup if referenced role missing | `doc/high-level.md` — Role Catalog (ap.iF4zXT5FUcqOzclp5JVHj.E) |

Additional spec coverage:
- `ContextForAgentProvider.md`: `RoleCatalogEntry` in `PlannerRequest`, `RoleCatalog` InstructionSection type
- `doc/high-level.md` Key Technology Decisions table: "Role catalog" and "Agent type + model" entries
- Planner instruction assembly includes role catalog for assigning roles to sub-parts