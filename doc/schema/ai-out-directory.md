# `.ai_out/` Directory Schema / ap.BXQlLDTec7cVVOrzXWfR7.E

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
│       └── comm/
│           ├── in/
│           │   └── instructions.md     # Instructions sent to this agent by the harness
│           └── out/
│               └── PUBLIC.md           # Agent work log (decisions, rationale, verdicts)
└── execution/                          # Execution phases
    └── ${part_name}/                   # Iteration group (e.g., ui_design, backend)
        └── ${sub_part}/                # Sub-part (e.g., impl, review, security_review). Order from array position in JSON.
            └── comm/
                ├── in/
                │   └── instructions.md # Instructions sent to this agent by the harness
                └── out/
                    └── PUBLIC.md       # Agent work log (decisions, rationale, verdicts)
```

## Key Files

| File | Scope | Purpose |
|------|-------|---------|
| `PUBLIC.md` | Per sub-part (`comm/out/`) | **Agent work log** — decisions made + rationale, what was implemented/reviewed, review verdicts. Overwritten each iteration; history preserved in git. **Required**: the harness validates existence and non-emptiness after every `done` signal (ref.ap.THDW9SHzs1x2JN9YP9OYU.E). |
| `instructions.md` | Per sub-part (`comm/in/`) | **Instructions from harness to agent** — the assembled instruction file containing role definition, ticket, shared context, prior outputs, and callback script usage. Overwritten each iteration; history preserved in git. |
| `SHARED_CONTEXT.md` | Branch-wide | **Shared knowledge base** about the codebase — discoveries, anchor points of interest, cross-cutting constraints, patterns/conventions observed. Mutable by all agents, accumulated across the workflow. |
| `current_state.json` | harness_private/ | Plan blueprint + execution progress — single source of truth for what to do and where we are. Written for progress tracking; consumed on restart in V2 (ref.ap.LX1GCIjv6LgmM7AJFas20.E). See [plan-and-current-state schema](plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E). |
| `plan.json` | harness_private/ | Planner's raw output (with-planning only). Becomes `current_state.json` after planning converges. Deleted after conversion. See [plan-and-current-state schema](plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E). |
| `PLAN.md` | shared/plan/ | Human-readable plan (with-planning only). Genuinely useful for any agent to understand the big picture. |

## Structure Decisions

### No PRIVATE.md

Agents do not have private output files. An agent's private state lives in its conversation history
(within its TMUX session). `PUBLIC.md` is the single output artifact per sub-part — everything an agent
writes is intended to be shared.

### Communication Visibility via `comm/`

Every sub-part has a `comm/` directory with `in/` (harness→agent) and `out/` (agent→harness).
This makes the full communication loop visible: `instructions.md` captures what the harness
told the agent, `PUBLIC.md` captures what the agent produced. Both are overwritten each
iteration — **git history preserves the full communication timeline**. This means when looking
at a commit, you see the instruction (input) and the agent's work (output) together,
providing a complete picture of each communication round without maintaining separate history
files.

### What Goes Where — PUBLIC.md vs SHARED_CONTEXT.md

| PUBLIC.md (agent work log) | SHARED_CONTEXT.md (shared knowledge base) |
|---|---|
| Decisions this agent made + succinct rationale | Codebase discoveries (e.g., "project uses X library v2.3") |
| What was implemented or reviewed | Anchor points of interest found (e.g., ref.ap.XXX) |
| Review verdicts + feedback | Cross-cutting constraints (e.g., "CI requires flag Y") |
| Part-specific trade-offs | Patterns/conventions observed in the codebase |

**Principle**: PUBLIC.md answers "what did I do and why." SHARED_CONTEXT.md answers "what did
I learn about the codebase that others need to know."

- PUBLIC.md should NOT duplicate decisions already captured in the plan or SHARED_CONTEXT.md.
- SHARED_CONTEXT.md is **mutable** — later agents refine what earlier agents wrote (e.g.,
  agent A discovers a library, agent B discovers the workaround for its bug, updates in place).
  This avoids forcing downstream agents to reconcile contradictory PUBLIC.md files.
- These writing guidelines are given to agents in their instruction files
  (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E).

### Parts and Sub-Parts

- **Part** = iteration group. Groups sub-parts that may loop (e.g., impl ↔ review).
- **Sub-part** = one unit of work by one agent. Numbered for execution order within the part.
- Role and agent type metadata live in `current_state.json` / workflow JSON, not in directory names.

## Scoping Rules

- **Branch isolation**: Each git branch gets its own `.ai_out/${branch}/` tree. No cross-branch sharing.
- **Part isolation**: Each part (`execution/${part_name}/`) is a self-contained iteration group.
- **Sub-part isolation**: Each sub-part has its own `comm/` directory for instructions and PUBLIC.md.

## Cross-Agent Visibility

Agents do **not** discover other agents' `PUBLIC.md` files themselves. The **instruction creator**
(`ContextForAgentProvider` — ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) is responsible for gathering pointers
to relevant `PUBLIC.md` files and including them in the agent's instruction file at assembly time.
This avoids a stale index file and keeps the harness in control of what each agent sees.

## No External Paths

All agent communication artifacts (instructions, outputs) live inside `.ai_out/` under the
`comm/` subdirectories. There are **no temporary files** outside the repo. The server port is
configured via the `TICKET_SHEPHERD_SERVER_PORT` environment variable
(see [Agent-to-Server Communication Protocol](../core/agent-to-server-communication-protocol.md)
ref.ap.wLpW8YbvqpRdxDplnN7Vh.E).

## Initial Creation

`TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) creates the full `.ai_out/${branch}/`
directory structure as part of ticket setup, including an **empty `SHARED_CONTEXT.md`** file.
This ensures the file exists before the first agent runs — `ContextForAgentProvider`
(ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) can always include it in instruction assembly without
checking for existence. Agents modify it in place.

## Codified In

To be rebuilt: `AiOutputStructure` class for path resolution + `ensureStructure()` for directory creation.
Previous implementation deleted for clean rebuild against the new schema.
