---
id: nid_j54dq6ra33hix1e8aavanb8bz_E
title: "High level approach on how we are going to work with the agent"
status: open
deps: []
links: []
created_iso: 2026-03-07T14:51:08Z
status_updated_iso: 2026-03-07T14:51:08Z
type: task
priority: 3
assignee: nickolaykondratyev
---

# High-Level Design Decisions

## What is the Harness?

The Kotlin CLI **replaces** the TOP_LEVEL_AGENT Claude session. It:
- Reads a task/ticket
- Orchestrates workflow phases (CLARIFICATION → IMPLEMENTATION → REVIEW → ITERATION)
- Spawns code agents (Claude Code, Droid, etc.) as sub-processes
- Manages file-based communication (PUBLIC.md / PRIVATE.md / SHARED_CONTEXT.md)
- Handles git commits between phases
- Monitors convergence and stopping conditions
- Can call `DirectLLMApi` for its own decisions (not everything is hardcoded Kotlin logic)

## Sub-Agent Invocation

- **Shell out** to CLI tools (`claude`, `droid`, etc.) behind a `CodeAgent` abstraction
- Reason: leverages subscription pricing; abstraction allows swapping implementations
- **Strictly serial** execution for V1 (parallel can be delegated to the agent itself)

## CodeAgent Abstraction (rough)

```
CodeAgent.run(
    instructionFile: Path,       // Markdown file with full instructions
    workingDir: Path,
    publicOutputFile: Path,      // explicit PUBLIC.md path
    privateOutputFile: Path,     // explicit PRIVATE.md path
) -> AgentResult { exitCode, stdout }
```

- Instructions written to **Markdown file** (preserves formatting vs. prompt text)
- `publicOutputFile` / `privateOutputFile` are explicit — no generic `outputFiles` list
- V1: no tool restrictions (allow everything)

## DirectLLMApi

For harness-internal quick tasks (compress ticket title, suggest feature name, etc.):

```
DirectLLMApi.askModel(prompt: String, tier: ModelTier): String

enum ModelTier { QuickCheap, Medium }
```

- Each `ModelTier` maps to a separate API provider (e.g., GLM for quick, GPT for medium)
- Provider is configurable per tier

## User Interaction — REVISED (V1 simplification)

**Prototyped and confirmed:** Kotlin can spawn `claude` (and other CLI agents) in fully
interactive mode via `InteractiveProcessRunner` (`ProcessBuilder` + `/dev/tty` redirect).
The user sees a live `claude` session in their terminal; Kotlin resumes after exit.
And we will be using `claude` (and other agents) through a TMUX session to be able to
interact with them.

- Why use TMUX: so that harness can send messages to Agents that we have spawned.

**This eliminates the sentinel pattern for V1.** Instead of agents writing
`#QUESTION_FOR_HUMAN:` markers and the harness scanning for them, we simply run agents
interactively — they ask the human directly, the human responds, the agent continues.

- **All agent invocations are interactive** (not just CLARIFICATION)
- Human questions are handled natively by the agent's own UX 
  - (human will be expected to check on tmux sessions)
- `SHARED_CONTEXT.md` is still useful for cross-agent context, but not for Q&A routing

**Caveat:** Must run via `./run.sh` (or `./app/build/install/app/bin/app` directly) —
Gradle's `:app:run` task breaks TTY detection. `run.sh` handles `installDist` + bin invocation.

## File Structure

```
.ai_out/${feature}/${git_branch}/
├── sub-agent/
│   ├── shared/
│   │   ├── SHARED_CONTEXT.md                          # Q&A, cross-cutting context for ALL agents
│   │   └── LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt  # links to all PUBLIC.md files
│   ├── harness/
│   │   └── PRIVATE.md                                 # harness internal state
│   └── ${ROLE}/
│       ├── PUBLIC.md
│       └── PRIVATE.md
```

## Agent Role Definitions

- Each ROLE must have a corresponding Markdown file in `${AGENTS_DIR}/`
- **Fail-fast on startup** if role file is missing (before invoking any models)
- Role file is passed to the invoked agent alongside:
  - `SHARED_CONTEXT.md`
  - `LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt`
  - Phase-specific artifacts

## Workflow Definition — Hybrid (Kotlin + XML)

Core engine in Kotlin; workflow phases defined in XML:

```xml
<workflow name="straightforward">
  <phase name="IMPLEMENTATION" role="IMPLEMENTOR_WITH_SELF_PLAN"
         mode="read-write" />
  <phase name="REVIEW" role="IMPLEMENTATION_REVIEWER"
         mode="read-only" depends-on="IMPLEMENTATION" />
  <iteration over="IMPLEMENTATION,REVIEW" max="4" />
</workflow>
```

## Git Branch / Feature Naming

- Tied to **note tickets**: branch = `{note_ticket_id}_{compressed_title}`
- Harness suggests `${feature}` if not provided
- Uses `DirectLLMApi(QuickCheap)` to compress long titles

## Clarification Phase

- Spawn `claude` (or other agent) in **interactive mode** via `InteractiveProcessRunner`
- User interacts directly with the agent; Kotlin resumes after exit
- **PROTOTYPED AND WORKING** — see `InteractiveProcessRunner.kt`
- On CTRL+C: agent exits (exit code -1 / interrupted=true); Kotlin harness continues
  - No custom signal handling needed — `process.waitFor()` unblocks on child exit

## V1 Scope

1. Single straightforward flow: IMPLEMENTATION_WITH_SELF_PLAN → REVIEW → ITERATION
   2. This flow should be defined in XML let's put these flows under
      3. ./config/workflows
2. File-based communication (PUBLIC.md / PRIVATE.md) between agents
3. Git commits between phases
4. Convergence check (max iterations)
5. **Interactive agent invocation** — human questions handled by agent directly (no sentinel)
6. `CodeAgent` abstraction (ClaudeCode impl) backed by `InteractiveProcessRunner`
7. `DirectLLMApi` for harness decisions
8. XML workflow definition

9. During spawn of the agent we should pass the files that the agent should read.
   10. We should filter the files that actually exist but mention the paths of the files that do not.

--------------------------------------------------------------------------------

Shutdown of sub-agent:
- Remember the agents are spawned in interactive mode, we are going to wait for them to create a DONE.md marker file under their directory.
