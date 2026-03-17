# `.ai_out/` Directory Schema / ap.BXQlLDTec7cVVOrzXWfR7.E

## Overview

All agent artifacts live under `.ai_out/${git_branch}/`. Each branch gets its own isolated tree.
The git branches will include ticket ids which guarantees not clashing.

## Directory Tree

```
.ai_out/${git_branch}/
├── harness_private/
│   ├── current_state.json              # Plan blueprint + execution progress + session IDs (single source of truth)
│   └── plan_flow.json                  # Planner output (with-planning only); becomes current_state.json after convergence
├── shared/
│   └── plan/
│       └── PLAN.md                     # Human-readable plan (with-planning only)
├── planning/                           # Planning loop (with-planning workflow only)
│   └── ${sub_part}/                    # e.g., plan, plan_review (order from array position in JSON)
│       ├── private/
│       │   └── PRIVATE.md             # Self-compaction context summary (only after session rotation)
│       └── comm/
│           ├── in/
│           │   └── instructions.md     # Instructions sent to this agent by the harness
│           └── out/
│               └── PUBLIC.md           # Agent work log (decisions, rationale, verdicts)
└── execution/                          # Execution phases
    └── ${part_name}/                   # Iteration group (e.g., ui_design, backend)
        ├── __feedback/                 # Granular feedback items (ref.ap.3Hskx3JzhDlixTnvYxclk.E)
        │   ├── pending/               #   Reviewer writes feedback files here (severity in filename prefix)
        │   ├── addressed/             #   Harness moves here after doer addresses
        │   └── rejected/              #   Harness moves here after reviewer accepts rejection
        │                              #   — see full spec: ref.ap.5Y5s8gqykzGN1TVK5MZdS.E
        └── ${sub_part}/                # Sub-part (e.g., impl, review, security_review). Order from array position in JSON.
            ├── private/
            │   └── PRIVATE.md         # Self-compaction context summary (only after session rotation)
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
| `PRIVATE.md` | Per sub-part (`private/`) | **Self-compaction context summary** — written by an agent during harness-controlled self-compaction (ref.ap.8nwz2AHf503xwq8fKuLcl.E). Contains compressed but context-rich summary of the agent's work, decisions, challenges, and codebase discoveries. Only present after a session rotation triggered by context window exhaustion. Overwritten on subsequent self-compactions; history preserved in git. Fed to the next session via `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). |
| `instructions.md` | Per sub-part (`comm/in/`) | **Instructions from harness to agent** — the assembled instruction file containing role definition, ticket, shared context, prior outputs, and callback script usage. Overwritten each iteration; history preserved in git. |
| `current_state.json` | harness_private/ | Plan blueprint + execution progress — single source of truth for what to do and where we are. Written for progress tracking; consumed on restart in V2 (ref.ap.LX1GCIjv6LgmM7AJFas20.E). See [plan-and-current-state schema](plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E). |
| `plan_flow.json` | harness_private/ | Planner's raw output (with-planning only). Strict machine-readable workflow definition: which agent roles, models, and iteration budgets to use. Consumed and validated by the harness. Becomes `current_state.json` after planning converges. Deleted after conversion. See [plan-and-current-state schema](plan-and-current-state.md) (ref.ap.56azZbk7lAMll0D4Ot2G0.E). |
| `PLAN.md` | shared/plan/ | Human-readable plan (with-planning only). Captures the *what and how* to implement: clarified requirements, tradeoffs decided during planning, architecture constraints, affected file paths, and design decisions. Consumed by all doer sub-parts in `with-planning` workflows — not parsed by the harness. See [What Goes Where — plan_flow.json vs PLAN.md](#what-goes-where--plan_flowjson-vs-planmd) below. |

### `__feedback/` — Granular Feedback Items

Lives at the **part level** (not sub-part level). Shared between doer and reviewer within
a part. Contains individual feedback items written by the reviewer as separate markdown
files — one per actionable issue. Severity is encoded in filename prefixes (`critical__`,
`important__`, `optional__`). The harness drives an inner loop that feeds items to the
doer one at a time in severity order (critical → important → optional).

**Harness owns all file movement.** Agents write a `## Resolution: ADDRESSED` or
`## Resolution: REJECTED` marker in the feedback file. The harness reads this marker and
moves the file to `addressed/` or `rejected/` accordingly. When the doer rejects, the
harness triggers a per-item rejection negotiation with the reviewer (bounded at 2
disagreement rounds). Agents never move feedback files between directories.

**Why move files when markers are the source of truth?** The `## Resolution` marker drives
harness logic — it is the canonical state. The directory movement is a **deliberate redundancy
for observability**: `ls pending/` instantly shows unresolved items, `ls addressed/` shows
completed work, `ls rejected/` shows accepted rejections. This makes directory listings a
zero-parsing state dashboard for human debugging. Git history also benefits — file renames
map 1:1 to state transitions, making the feedback lifecycle auditable without inspecting file
contents.

Three directories (`pending/`, `addressed/`, `rejected/`) are created by
`AiOutputStructure.ensureStructure()` at part setup — empty at creation. Not visible to
agents in other parts.

Full spec: [`doc/plan/granular-feedback-loop.md`](../plan/granular-feedback-loop.md)
(ref.ap.5Y5s8gqykzGN1TVK5MZdS.E). Directory concept: ref.ap.3Hskx3JzhDlixTnvYxclk.E.

---

## Structure Decisions

### PRIVATE.md — Self-Compaction Context

`PRIVATE.md` lives under `${sub_part}/private/` and is written **only** during
harness-controlled self-compaction (ref.ap.8nwz2AHf503xwq8fKuLcl.E). It is NOT a
general-purpose private output file — it exists specifically to bridge context across
session rotations when the context window fills up.

- **Not present by default.** Most sub-parts will never self-compact. `PRIVATE.md` only
  appears after context window exhaustion triggers session rotation.
- **Written by the agent, consumed by the harness.** The agent writes it during
  self-compaction. `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) includes
  it in the next session's instructions if present.
- **Overwritten on subsequent compactions.** Git preserves history.
- **Not visible to other sub-parts.** Only the same sub-part's next session reads it.

### Communication Visibility via `comm/`

Every sub-part has a `comm/` directory with `in/` (harness→agent) and `out/` (agent→harness).
This makes the full communication loop visible: `instructions.md` captures what the harness
told the agent, `PUBLIC.md` captures what the agent produced. Both are overwritten each
iteration — **git history preserves the full communication timeline**. This means when looking
at a commit, you see the instruction (input) and the agent's work (output) together,
providing a complete picture of each communication round without maintaining separate history
files.

### What Goes Where — plan_flow.json vs PLAN.md

These two files are produced by the PLANNER agent and serve entirely different consumers and purposes:

| `plan_flow.json` (workflow definition) | `PLAN.md` (implementation guidance) |
|---|---|
| Strict machine-readable format — parsed and validated by the harness | Human-readable markdown — read by implementation agents |
| Which agent roles to run, in what order, with which models | Clarified requirements that didn't exist in the original ticket |
| Iteration budgets per reviewer | Tradeoffs decided during planning and why |
| `agentType` per sub-part | Architecture constraints and relevant file paths |
| Consumed by the harness to drive execution | Passed to all doer sub-parts in `with-planning` workflows |

**Why both exist:** `plan_flow.json` tells the harness *how to run the workflow* (which agents,
what model, in what order). `PLAN.md` tells implementation agents *what to build and how* — it
is the implementer's guide, containing everything needed to do the work confidently without
second-guessing planning decisions. Single source of truth for each distinct consumer; no risk
of divergence because they carry different information.

### What Goes Where — PUBLIC.md vs PLAN.md

| PUBLIC.md (agent work log) | PLAN.md (plan document) |
|---|---|
| Decisions this agent made + succinct rationale | Architectural decisions from planning phase |
| What was implemented or reviewed | Cross-cutting constraints and approach |
| Review verdicts + feedback | Part decomposition and sequencing rationale |
| Codebase discoveries, anchor points, patterns observed | Big-picture plan for all agents |

**Principle**: PUBLIC.md answers "what did I do, what did I learn, and why." PLAN.md answers
"what is the overall plan and approach." PRIVATE.md answers "what do I need to remember to
continue my work in a fresh session."

- PUBLIC.md should NOT duplicate content already in the plan.
- Cross-cutting knowledge (codebase discoveries, anchor points, patterns) goes in PUBLIC.md
  — `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) includes prior PUBLIC.md files
  in downstream agents' instructions, so discoveries propagate naturally.
- **Durable reasoning** should be captured in the code itself (WHY-NOT comments), persistent
  documentation (`CLAUDE.md`, deep memory, `.md` notes), and the ticket — not just in
  PUBLIC.md. This ensures reasoning survives beyond the current workflow iteration.
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
directory structure as part of ticket setup, including the `private/` directory under each
sub-part. This ensures the directory structure exists before the first agent runs —
`ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) can always check for
`PRIVATE.md` without worrying about the parent directory. `PRIVATE.md` itself is **not**
created at initialization — it is only written by agents during self-compaction
(ref.ap.8nwz2AHf503xwq8fKuLcl.E).

## Codified In

To be rebuilt: `AiOutputStructure` class for path resolution + `ensureStructure()` for directory creation.
Previous implementation deleted for clean rebuild against the new schema.
