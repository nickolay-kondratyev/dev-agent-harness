---
id: nid_m5e0q3oslsihsxsz1h6no6nwr_E
title: "Design and implement agent role catalog"
status: in_progress
deps: []
links: []
created_iso: 2026-03-12T23:19:46Z
status_updated_iso: 2026-03-18T14:53:35Z
type: feature
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [roles, catalog]
---

GOAL: MERGE the info into the specs right now DO NOT IMPLEMENT IT YET.

TICKET AS IS to merge into the specs and close the ticket:
--------------------------------------------------------------------------------
Design the agent role catalog system:
- Role definitions live in $_config/agents/input/*.md (authoritative source)
- generate.sh produces $_config/agents/_generated/*.md (runtime files)
- $TICKET_SHEPHERD_AGENTS_DIR env var points to _generated/
- Each role .md file has YAML frontmatter with required `description` and optional `description_long`
- Roles define behavior only — no agentType/model (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E)
- Auto-discovered at startup: every .md file in $TICKET_SHEPHERD_AGENTS_DIR is an eligible role
- Fail-fast on startup if a role referenced in a straightforward workflow is missing

Spec references:
- Role catalog: doc/high-level.md (ap.iF4zXT5FUcqOzclp5JVHj.E)
- Directory structure: doc/high-level.md (ap.Q7kR9vXm3pNwLfYtJ8dZs.E)
- Agent type & model assignment: doc/high-level.md (ap.Xt9bKmV2wR7pLfNhJ3cQy.E)
- Env var validation: doc/core/git.md (TICKET_SHEPHERD_AGENTS_DIR)
--------------------------------------------------------------------------------