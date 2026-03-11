# `.ai_out/` Directory Schema/ ap.BXQlLDTec7cVVOrzXWfR7.E

<!-- ref.ap.XBNUQHLjDLpAr8F9IOyXU.E — AiOutputStructure class codifies this schema -->

## Overview

All agent artifacts live under `.ai_out/${git_branch}/`. Each branch gets its own isolated tree.

## Directory Tree

```
.ai_out/${git_branch}/
├── harness_private/
│   ├── current_state.json              # Serialized workflow state (incl. resolved parts); enables resume
│   └── PRIVATE.md                      # Harness internal context (if needed)
├── shared/
│   ├── SHARED_CONTEXT.md               # Cross-cutting context for ALL agents (agents can modify)
│   └── plan/
│       ├── PLAN.md                     # Human-readable plan (with-planning only)
│       └── plan.json                   # Machine-readable plan (with-planning only)
├── planning/                           # Planning loop (with-planning workflow only)
│   └── ${ROLE}/                        # e.g., PLANNER, PLAN_REVIEWER
│       ├── PUBLIC.md
│       ├── PRIVATE.md
│       └── session_ids/
│           └── ${timestamp}.json       # Session ID + agent type
└── phases/                             # Execution phases
    └── ${part_name}/                   # e.g., part_1, ui_design, backend_impl
        └── ${ROLE}/                    # e.g., IMPLEMENTOR, REVIEWER
            ├── PUBLIC.md
            ├── PRIVATE.md
            └── session_ids/
                └── ${timestamp}.json   # Session ID + agent type
```

## Key Files

| File | Scope | Purpose |
|------|-------|---------|
| `PUBLIC.md` | Per role per part/planning | Agent's public output — visible to other agents and used in iteration evaluation |
| `PRIVATE.md` | Per role per part/planning | Agent's private notes — not shared with other agents |
| `SHARED_CONTEXT.md` | Branch-wide | Cross-cutting context all agents can read and write |
| `current_state.json` | Branch-wide | Workflow progress — which part/phase is current; enables resume |
| `PLAN.md` | Branch-wide (shared/plan/) | Human-readable plan (with-planning workflow only) |
| `plan.json` | Branch-wide (shared/plan/) | Machine-readable plan parsed by harness (with-planning workflow only) |
| `${timestamp}.json` | Per role session_ids/ | Persisted session ID + agent type for resume capability |

## Scoping Rules

- **Branch isolation**: Each git branch gets its own `.ai_out/${branch}/` tree. No cross-branch sharing.
- **Part isolation**: Each part (`phases/${part_name}/`) is a self-contained unit of work.
- **Role isolation**: Within a part, each role has its own directory for PUBLIC/PRIVATE/session_ids.

## Cross-Agent Visibility

Agents do **not** discover other agents' `PUBLIC.md` files themselves. The **instruction creator** (ContextProvider)
is responsible for gathering pointers to relevant `PUBLIC.md` files and including them in the agent's
instruction file at assembly time. This avoids a stale index file and keeps the harness in control of
what each agent sees.

## Iteration Behavior

- **Within a part**: When review fails and iteration loops back, the **same** `phases/${part_name}/${ROLE}/` directory is reused. The agent reads its own prior `PUBLIC.md` for context on what to fix.
- **Within planning**: Same pattern — `planning/PLANNER/` and `planning/PLAN_REVIEWER/` reused across planning iterations. Each iteration overwrites `PUBLIC.md`.

## External Paths (outside repo)

```
$HOME/.chainsaw_agent_harness/
├── server/
│   └── port.txt                        # Harness HTTP server port (written on startup, deleted on shutdown)
└── tmp/
    └── agent_comm/                     # Temp instruction/answer files sent to agents via TMUX send-keys
```

## Codified In

`AiOutputStructure` class (`app/src/main/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructure.kt`) — pure path resolution + `ensureStructure()` for directory creation.
