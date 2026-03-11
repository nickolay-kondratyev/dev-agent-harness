# `.ai_out/` Directory Schema/ ap.BXQlLDTec7cVVOrzXWfR7.E

<!-- ref.ap.XBNUQHLjDLpAr8F9IOyXU.E — AiOutputStructure class codifies this schema -->

## Overview

All agent artifacts live under `.ai_out/${git_branch}/`. Each branch gets its own isolated tree.

## Directory Tree

```
.ai_out/${git_branch}/
├── harness_private/
│   └── current_state.json              # Serialized workflow state (incl. resolved parts); enables resume
├── shared/
│   ├── SHARED_CONTEXT.md               # Cross-cutting context for ALL agents (agents can modify)
│   └── plan/
│       ├── PLAN.md                     # Human-readable plan (with-planning only)
│       └── plan.json                   # Machine-readable plan (with-planning only)
├── planning/                           # Planning loop (with-planning workflow only)
│   └── ${sub_part}/                    # e.g., 1_plan, 2_plan_review (numbered for execution order)
│       ├── PUBLIC.md
│       └── session_ids/
│           └── ${timestamp}.json       # Session ID + agent type
└── phases/                             # Execution phases
    └── ${part_name}/                   # Iteration group (e.g., ui_design, backend)
        └── ${sub_part}/                # Numbered sub-part (e.g., 1_impl, 2_review, 3_security_review)
            ├── PUBLIC.md
            └── session_ids/
                └── ${timestamp}.json   # Session ID + agent type
```

## Key Files

| File | Scope | Purpose |
|------|-------|---------|
| `PUBLIC.md` | Per sub-part | Agent's output — the sole communication channel between agents |
| `SHARED_CONTEXT.md` | Branch-wide | Cross-cutting context all agents can read and write |
| `current_state.json` | Branch-wide | Workflow progress — which part/sub-part is current; enables resume |
| `PLAN.md` | Branch-wide (shared/plan/) | Human-readable plan (with-planning workflow only) |
| `plan.json` | Branch-wide (shared/plan/) | Machine-readable plan parsed by harness (with-planning workflow only) |
| `${timestamp}.json` | Per sub-part session_ids/ | Persisted session ID + agent type for resume capability |

## Structure Decisions

### No PRIVATE.md

Agents do not have private output files. An agent's private state lives in its conversation history
(available via resume). `PUBLIC.md` is the single output artifact per sub-part — everything an agent
writes is intended to be shared.

### Parts and Sub-Parts

- **Part** = iteration group. Groups sub-parts that may loop (e.g., impl ↔ review).
- **Sub-part** = one unit of work by one agent. Numbered for execution order within the part.
- Role and agent type metadata live in `plan.json`, not in directory names.

### plan.json Schema

```json
{
  "parts": [
    {
      "name": "ui_design",
      "subParts": [
        { "name": "1_impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode" },
        { "name": "2_review", "role": "UI_REVIEWER", "agentType": "ClaudeCode",
          "iteration": { "max": 3, "loopsBackTo": "1_impl" } },
        { "name": "3_security_review", "role": "SECURITY_REVIEWER", "agentType": "PI",
          "iteration": { "max": 2, "loopsBackTo": "1_impl" } }
      ]
    }
  ]
}
```

- `iteration` on a review sub-part defines the impl ↔ review loop budget.
- `loopsBackTo` names the sub-part to return to when review fails — typically the implementor.
- The implementor can fix or push back on feedback; then the reviewer runs again.
- Sub-parts without `iteration` execute once.

## Scoping Rules

- **Branch isolation**: Each git branch gets its own `.ai_out/${branch}/` tree. No cross-branch sharing.
- **Part isolation**: Each part (`phases/${part_name}/`) is a self-contained iteration group.
- **Sub-part isolation**: Each sub-part has its own directory for PUBLIC.md and session_ids.

## Cross-Agent Visibility

Agents do **not** discover other agents' `PUBLIC.md` files themselves. The **instruction creator** (ContextProvider)
is responsible for gathering pointers to relevant `PUBLIC.md` files and including them in the agent's
instruction file at assembly time. This avoids a stale index file and keeps the harness in control of
what each agent sees.

## Iteration Behavior

- **Single `PUBLIC.md` per sub-part**, overwritten each iteration. The agent is responsible for
  including relevant context in its output.
- **Trackability via git**: The harness commits between iterations, so the full history of `PUBLIC.md`
  changes is preserved in git — no need for versioned files.
- **Fresh reviewer**: To use a brand new reviewer (no prior conversation), the harness deletes the
  reviewer's `PUBLIC.md` and starts a new session (no resume). The fresh reviewer picks up context
  purely from the implementor's `PUBLIC.md` and other files assembled by ContextProvider.
- **Resumed reviewer**: To continue with the same reviewer, the harness resumes the session.
  The reviewer retains its full conversation history.

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
