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

# Chainsaw — High-Level Design (V1)

Codename: **CHAINSAW**. Will appear in package names (manual rename, not part of this ticket).

## Context

Previously, a TOP_LEVEL_AGENT Claude session orchestrated sub-agents. Problem: sub-agent context
polluted the orchestrator's context window. Solution: a **Kotlin CLI harness** replaces the
orchestrator. Sub-agents are spawned as independent processes — their context is fully isolated.

## What the Harness Does

- Reads a task/ticket
- Orchestrates workflow phases (defined in XML)
- Spawns code agents (Claude Code, Droid, etc.) via **TMUX**
- Runs a **local HTTP server** (Ktor CIO) for agent→harness callbacks
- Manages file-based context (PUBLIC.md / PRIVATE.md / SHARED_CONTEXT.md)
- Handles git commits between phases
- Monitors convergence and stopping conditions
- Uses `DirectLLMApi` for its own decisions (not everything is hardcoded Kotlin logic)

---

## Sub-Agent Invocation — TMUX Only

All agents are spawned as **interactive TMUX sessions**. `InteractiveProcessRunner` was a prototype
stepping stone — V1 uses TMUX exclusively via `TmuxSessionManager` + `TmuxCommunicator`.

- `CodeAgent` abstraction with `ClaudeCodeAgent` implementation
- Leverages subscription pricing; abstraction allows swapping agent implementations
- **Strictly serial** execution for V1 (1 harness → 1 TMUX session at a time)
- **Separate sessions per phase** — each phase spawns a fresh agent. Context carries via files.
- Future: parallel sessions on separate git worktrees (branch as identifier)

### CodeAgent Abstraction (rough)

```
CodeAgent.run(
    instructionFile: Path,       // Markdown file with full instructions
    workingDir: Path,
    publicOutputFile: Path,      // explicit PUBLIC.md path
    privateOutputFile: Path,     // explicit PRIVATE.md path
) -> AgentResult { exitCode, stdout }
```

- Instructions written to Markdown file (preserves formatting vs. prompt text)
- V1: no tool restrictions (allow everything)

---

## Agent↔Harness Communication — Server + CLI

### Architecture

```
┌────────────┐    HTTP (curl)     ┌──────────────────┐
│  Agent      │ ──────────────→  │  Harness Server   │
│  (in TMUX)  │                   │  (Ktor CIO)       │
│             │ ←──────────────  │                    │
│             │   TMUX send-keys  │                    │
└────────────┘                   └──────────────────┘
```

**Harness** starts a Ktor CIO HTTP server on a fixed port.
- Port is set via env var: `CHAINSAW_AGENT_HARNESS__SERVER_PORT`
- This env var is exported into the TMUX session before agent starts

**Agent** communicates back using a **bash CLI script**: `harness-cli-for-agent.sh`
- Lives on `$PATH` of the started agent
- Wraps `curl` calls to the harness server
- Agent receives `--help` content in its instructions, wrapped in
  `<critical_to_keep_through_compaction>` tags to survive context compaction

### V1 Server Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /agent/done` | Agent completed its task. Harness kills TMUX session, proceeds to next phase. |
| `POST /agent/question` | Agent has questions for human. Agent blocks. Harness surfaces question to user, gets answer, sends response back via TMUX `send-keys`. |
| `POST /agent/failed` | Unrecoverable error. Harness handles accordingly. |

All requests include the **git branch** as identifier (key for future parallelism).

### Q&A Flow

1. Agent calls `harness-cli-for-agent.sh question "How should I handle X?"`
2. CLI POSTs to `/agent/question` with branch + question text
3. Harness presents question to human (stdout/interactive)
4. Human answers
5. Harness sends answer back to agent via TMUX `send-keys`
6. Agent continues

### Agent Lifecycle

1. Harness creates TMUX session, exports `CHAINSAW_AGENT_HARNESS__SERVER_PORT`
2. Harness starts agent (e.g., `claude`) in the TMUX session
3. Agent works, may call CLI for questions
4. Agent calls `harness-cli-for-agent.sh done` when finished
5. Harness receives `/agent/done`, kills TMUX session
6. Harness proceeds to next workflow phase

---

## Session ID Tracking — Wingman

**Problem:** Claude Code doesn't expose its session ID to the agent itself.

**Solution:** Wingman interface discovers session IDs externally.

1. Harness generates a GUID for each new session
2. Harness sends GUID to agent as first message: `"Here is a GUID: [$GUID]. We will use it to identify this session."`
3. `ClaudeCodeWingman` searches `$HOME/.claude/projects/.../*.jsonl` for files containing the GUID
4. Matched filename = session ID (e.g., `77d5b7ea-cf04-453b-8867-162404763e18.jsonl`)
5. Session ID stored in `.ai_out/${feature}/${git_branch}/${ROLE}/session_ids/${timestamp}.json`
6. Enables future session resumption if desired

---

## DirectLLMApi

For harness-internal quick tasks (compress ticket title, suggest feature name, etc.):

```
DirectLLMApi.askModel(prompt: String, tier: ModelTier): String

enum ModelTier { QuickCheap, Medium }
```

- Each `ModelTier` maps to a separate API provider (e.g., GLM for quick, GPT for medium)
- Provider is configurable per tier

---

## Workflow Definition — Hybrid (Kotlin + XML)

Core engine in Kotlin; workflow phases defined in XML under `./config/workflows/`:

```xml
<workflow name="straightforward">
  <phase name="IMPLEMENTATION" role="IMPLEMENTOR_WITH_SELF_PLAN"
         mode="read-write" />
  <phase name="REVIEW" role="IMPLEMENTATION_REVIEWER"
         mode="read-only" depends-on="IMPLEMENTATION" />
  <iteration over="IMPLEMENTATION,REVIEW" max="4" />
</workflow>
```

### Phase Transitions — Hybrid

- **Automatic** for straightforward transitions (e.g., IMPLEMENTATION done → REVIEW starts)
- **LLM-evaluated** for iteration decisions (e.g., does the review pass? use DirectLLMApi to decide)

---

## File Structure

```
.ai_out/${feature}/${git_branch}/
├── sub-agent/
│   ├── shared/
│   │   ├── SHARED_CONTEXT.md                              # cross-cutting context for ALL agents
│   │   └── LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt  # links to all PUBLIC.md files
│   ├── harness/
│   │   └── PRIVATE.md                                     # harness internal state (NOT shared with agents)
│   └── ${ROLE}/
│       ├── PUBLIC.md
│       ├── PRIVATE.md
│       └── session_ids/${timestamp}.json                  # session ID + agent type
```

## Agent Role Definitions

- Each ROLE has a corresponding Markdown file in `${AGENTS_DIR}/`
- **Fail-fast on startup** if role file is missing
- Role file is passed to the agent alongside:
  - `SHARED_CONTEXT.md`
  - `LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt`
  - Phase-specific artifacts
  - `harness-cli-for-agent.sh --help` content (in `<critical_to_keep_through_compaction>` tags)

## Git Branch / Feature Naming

- Tied to **note tickets**: branch = `{note_ticket_id}_{compressed_title}`
- Harness suggests `${feature}` if not provided
- Uses `DirectLLMApi(QuickCheap)` to compress long titles

---

## V1 Scope Summary

1. TMUX-based agent invocation (`CodeAgent` abstraction, `ClaudeCodeAgent` impl)
2. Ktor CIO HTTP server for agent→harness callbacks (done/question/failed)
3. Bash CLI script (`harness-cli-for-agent.sh`) for agents to call back
4. Wingman for session ID discovery
5. File-based cross-agent context (PUBLIC.md / PRIVATE.md / SHARED_CONTEXT.md)
6. XML workflow definition under `./config/workflows/`
7. Hybrid phase transitions (automatic + LLM-evaluated for iterations)
8. DirectLLMApi for harness decisions (GLM QuickCheap tier first)
9. Git commits between phases
10. Strictly serial execution (1 harness → 1 agent at a time)
11. Separate sessions per phase (no session resumption across phases in V1)
12. Agent instructions include files to read (filter existing, mention missing paths)
