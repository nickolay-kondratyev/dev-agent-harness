# `.ai_out/` Directory Schema / ap.BXQlLDTec7cVVOrzXWfR7.E

<!-- ref.ap.XBNUQHLjDLpAr8F9IOyXU.E — AiOutputStructure class codifies this schema -->

## Overview

All agent artifacts live under `.ai_out/${git_branch}/`. Each branch gets its own isolated tree.
The git branches will include ticket ids which guarantees not clashing.

## Directory Tree

```
.ai_out/${git_branch}/
├── harness_private/
│   ├── current_state.json              # Plan blueprint + execution progress + session IDs (single source of truth)
│   └── plan.json                       # Planner output (with-planning only); becomes current_state.json after convergence
├── shared/
│   ├── SHARED_CONTEXT.md               # Cross-cutting context for ALL agents (agents can modify)
│   └── plan/
│       └── PLAN.md                     # Human-readable plan (with-planning only)
├── planning/                           # Planning loop (with-planning workflow only)
│   └── ${sub_part}/                    # e.g., plan, plan_review (order from array position in JSON)
│       └── PUBLIC.md
└── execution/                          # Execution phases
    └── ${part_name}/                   # Iteration group (e.g., ui_design, backend)
        └── ${sub_part}/                # Sub-part (e.g., impl, review, security_review). Order from array position in JSON.
            └── PUBLIC.md
```

## Key Files

| File | Scope | Purpose |
|------|-------|---------|
| `PUBLIC.md` | Per sub-part | Agent's output — the main communication channel between agents. |
| `SHARED_CONTEXT.md` | Branch-wide | Cross-cutting context all agents can read and write |
| `current_state.json` | harness_private/ | Plan blueprint + execution progress — single source of truth for what to do and where we are. Enables resume. See [plan-and-current-state schema](plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E). |
| `plan.json` | harness_private/ | Planner's raw output (with-planning only). Becomes `current_state.json` after planning converges. Deleted after conversion. See [plan-and-current-state schema](plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E). |
| `PLAN.md` | shared/plan/ | Human-readable plan (with-planning only). Genuinely useful for any agent to understand the big picture. |

## Structure Decisions

### No PRIVATE.md

Agents do not have private output files. An agent's private state lives in its conversation history
(available via resume). `PUBLIC.md` is the single output artifact per sub-part — everything an agent
writes is intended to be shared.

- PUBLIC.md: should capture any key decisions that agent has made, it should NOT duplicate decisions that are already captured in the plan or shared context. But it should state decisions that it made and SUCCINCT WHY the decision was made. So that other agents are aware of the tradeoff that was made and rationale behind it. This direction of writing proper public.md file should be given to the agents in their instructions.

### Parts and Sub-Parts

- **Part** = iteration group. Groups sub-parts that may loop (e.g., impl ↔ review).
- **Sub-part** = one unit of work by one agent. Numbered for execution order within the part.
- Role and agent type metadata live in `current_state.json` / workflow JSON, not in directory names.

## Scoping Rules

- **Branch isolation**: Each git branch gets its own `.ai_out/${branch}/` tree. No cross-branch sharing.
- **Part isolation**: Each part (`execution/${part_name}/`) is a self-contained iteration group.
- **Sub-part isolation**: Each sub-part has its own directory for PUBLIC.md.

## Cross-Agent Visibility

Agents do **not** discover other agents' `PUBLIC.md` files themselves. The **instruction creator** (ContextProvider)
is responsible for gathering pointers to relevant `PUBLIC.md` files and including them in the agent's
instruction file at assembly time. This avoids a stale index file and keeps the harness in control of
what each agent sees.

## External Paths (outside repo)

```
$HOME/.shepherd_agent_harness/
├── server/
│   └── port.txt                        # Harness HTTP server port (written on startup, deleted on shutdown)
└── tmp/
    └── agent_comm/                     # Temp instruction/answer files sent to agents via TMUX send-keys
```

See [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md) (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for protocol details on port discovery and temp file delivery.

## Codified In

To be rebuilt: `AiOutputStructure` class for path resolution + `ensureStructure()` for directory creation.
Previous implementation deleted for clean rebuild against the new schema.
