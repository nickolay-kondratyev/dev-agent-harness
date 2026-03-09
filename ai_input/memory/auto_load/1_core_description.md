## Project Overview

Codename: **CHAINSAW**. Package: `com.glassthought.chainsaw`.

CLI Kotlin Agent Harness — replaces a top-level orchestrator agent with a Kotlin CLI process.
Sub-agents are spawned as independent processes with fully isolated context windows.

### Key Characteristics
- **CLI application** built in Kotlin (JVM)
- **Agent coordination**: manages workflow phases, file-based communication between agents
- **NOT thorg-specific** — general-purpose agent harness

### High-Level Architecture Decisions

**Ticket-driven**: Chainsaw always operates on a ticket (markdown file with YAML frontmatter containing `id` and `title`). The ticket is required input.

**CLI**: `chainsaw run --workflow <name> --ticket <path>` via **picocli**.

**Agent invocation — TMUX only**: All agents spawned as interactive TMUX sessions. Strictly serial (one agent at a time) in V1. Separate session per phase — context carries via files.

**Agent↔Harness communication — bidirectional**:
- Agent → Harness: HTTP POST via `harness-cli-for-agent.sh` (wraps curl)
- Harness → Agent: TMUX `send-keys` / ref.ap.7sZveqPcid5z1ntmLs27UqN6.E
- Structured content delivered via temp files (write file, send path)

**HTTP server**: Ktor CIO, binds port 0 (OS-assigned), writes port to `$HOME/.chainsaw_agent_harness/server/port.txt`. Starts once, stays alive across all phases.

**Workflow definitions**: JSON under `./config/workflows/`. Shared "parts" schema for both static and planner-generated workflows. **Jackson + Kotlin module** for all serialization.

**Two workflow types**:
- `straightforward` — static parts, no planning
- `with-planning` — PLANNER → PLAN_REVIEWER iteration loop, then dynamic execution from `plan.json`

**Role catalog**: Auto-discovered from `$CHAINSAW_AGENTS_DIR`. Every `.md` file is an eligible role; `description` extracted from YAML frontmatter.

**Session tracking**: `Wingman` interface (`ClaudeCodeWingman` impl) — GUID handshake to discover Claude Code session IDs for resume.

**Harness decisions**: `DirectLLMApi` for iteration evaluation, title compression, etc. Structured JSON responses. Tiers: `QuickCheap`, `Medium`.

**Server endpoints (V1)**: `POST /agent/done` (task complete), `/agent/question` (Q&A, curl blocks until human answers), `/agent/failed` (unrecoverable error → `FailedToExecutePlanUseCase`), `/agent/status` (health ping reply). All requests include git branch as identifier.

**CodeAgent abstraction**: `CodeAgent.run(instructionFile, workingDir, publicOutputFile, privateOutputFile) -> AgentResult`. Instructions are Markdown files. `ClaudeCodeAgent` is the V1 implementation.

**Context assembly**: `ContextProvider` interface assembles context packages — agent instruction files (role definition + ticket + SHARED_CONTEXT.md + prior PUBLIC.md files + harness CLI help), iteration decision prompts, and planner instructions (ticket + role catalog).

**Phase transitions — hybrid**: Automatic for straightforward transitions (implementor → reviewer). LLM-evaluated for iteration decisions: `DirectLLMApi` receives reviewer's PUBLIC.md + reviewed role's PUBLIC.md + SHARED_CONTEXT.md, returns structured JSON (pass/fail + reason).

**Agent lifecycle**: TMUX session created → agent started → Wingman GUID handshake → instruction file sent via `send-keys` → agent works (may call Q&A) → agent calls `/agent/done` → harness kills session → next phase.

**Health monitoring**: Timeout → ping via TMUX → crash detection. UseCase pattern (`NoStatusCallbackTimeOutUseCase`, `NoReplyToPingUseCase`, `FailedToExecutePlanUseCase`).

**Plan mutability**: Frozen during execution. Minor adjustments within a part OK. Major deviations → agent calls `/agent/failed` → cleanup agent enriches ticket → codebase reset → ticket re-opened.

**Resume**: `current_state.json` tracks workflow progress. On restart, offers to resume from last checkpoint.

**File structure**: `.ai_out/${git_branch}/` with `harness_private/`, `shared/`, `planning/`, `phases/` subdirectories.

**Git branching**: Derived from ticket. `{TICKET_ID}__{slugified_title}__try-{N}` (`__` delimiter). try-N starts at 1, increments on retry after failure.

### Dependencies
- Will take dependencies on well established third-party libraries.
- **Depends on asgardCore** (Out/OutFactory logging, ProcessRunner, AsgardCloseable, coroutines)
